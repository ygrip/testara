package io.github.ygrip.testara.ui.service;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.cucumber.java.Scenario;
import io.github.ygrip.testara.ui.context.StepContext;
import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.model.ScreenshotStrategy;
import io.github.ygrip.testara.ui.observation.Capture;
import io.github.ygrip.testara.ui.support.ScreenRecorder;
import lombok.extern.log4j.Log4j2;

@Log4j2
public final class ScreenshotService {
  private static final Map<String, String> recordingMap = new ConcurrentHashMap<>();

  public static void attachStepScreenshot(ScreenshotStrategy screenshotStrategy, Scenario scenario) {
    if (screenshotStrategy.equals(ScreenshotStrategy.ON_EACH_STEP)) {
      String stepName = StepContext.getStepName();
      takeScreenshot(stepName, scenario);
    }
  }

  public static void startRecording(Actor actor, int frameRates, boolean forceResolution, int bitRate, Scenario scenario) {
    if (actor == null) {
      log.debug("Skipping screen recording because no actor is available");
      return;
    }
    final var id = scenario.getId();
    if (!recordingMap.containsKey(id)) {
      try {
        String encodedParam = URLEncoder.encode(id, StandardCharsets.UTF_8);
        final var instance = ScreenRecorder.instance()
          .withActor(actor).forceResolution(forceResolution).bitRate(bitRate);

        instance.startRecording("./target/recording/" + encodedParam, frameRates);
        recordingMap.put(id, instance.outputPath());
      } catch (IllegalStateException err) {
        log.debug("Skipping screen recording because the driver is unavailable: {}", err.getMessage());
      } catch (Exception err) {
        log.error("Fail to record screen, {}", err.getMessage());
      }
    }
  }

  public static void attachRecording(ScreenshotStrategy screenshotStrategy, Scenario scenario) {
    final String scenarioName = scenario.getName();
    final String id = scenario.getId();

    try {
      CompletableFuture<File> recordingFuture = ScreenRecorder.instance()
        .stopRecordingAsync();

      File video = recordingFuture.join();

      if (video.exists() && video.length() > 0) {
        byte[] data = Files.readAllBytes(video.toPath());
        boolean shouldAttach =
          screenshotStrategy != ScreenshotStrategy.NONE && (screenshotStrategy != ScreenshotStrategy.ON_FAILURE
            || scenario.isFailed());

        if (shouldAttach) {
          scenario.attach(data, "video/mp4", scenarioName);
        }
      }

      recordingMap.remove(id);

    } catch (Exception err) {
      log.warn("Recording failed: {}", err.getMessage());
    }
  }

  private static void takeScreenshot(String name, Scenario scenario) {
    Actor actor;
    try {
      actor = ActorManager.currentActor();
    } catch (IllegalStateException e) {
      log.debug("Skipping screenshot because no driver session or actor is available: {}", e.getMessage());
      return;
    }
    if (actor == null) {
      log.debug("Skipping screenshot because no actor is available");
      return;
    }

    try {
      byte[] screenshot = actor.observe(Capture.page()
        .visibleOnViewPort());

      if (screenshot != null && screenshot.length > 0) {
        scenario.attach(screenshot, "image/png", name);
      }
    } catch (IllegalStateException e) {
      log.debug("Skipping screenshot because the driver is unavailable: {}", e.getMessage());
    } catch (Exception e) {
      log.warn("Screenshot failed: {}", e.getMessage());
    }
  }

  public static void attachScenarioScreenshot(ScreenshotStrategy screenshotStrategy, Scenario scenario) {
    String scenarioName = scenario.getName();
    if (screenshotStrategy.equals(ScreenshotStrategy.ON_FAILURE)) {
      if (scenario.isFailed()) {
        takeScreenshot(scenarioName, scenario);
      }
    } else if (screenshotStrategy.equals(ScreenshotStrategy.ON_EACH_SCENARIO)) {
      takeScreenshot(scenarioName, scenario);
    }
  }
}
