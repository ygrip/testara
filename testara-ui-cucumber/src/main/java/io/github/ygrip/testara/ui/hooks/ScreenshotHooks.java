package io.github.ygrip.testara.ui.hooks;

import io.cucumber.java.After;
import io.cucumber.java.AfterStep;
import io.cucumber.java.BeforeStep;
import io.cucumber.java.Scenario;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.ui.config.EngineProperties;
import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.model.ScreenshotOutputType;
import io.github.ygrip.testara.ui.model.ScreenshotQuality;
import io.github.ygrip.testara.ui.model.ScreenshotStrategy;
import io.github.ygrip.testara.ui.service.ScreenshotService;

@TestComponent(scope = RegistryScope.TEST)
public class ScreenshotHooks {
  private ScreenshotStrategy screenshotStrategy;
  private ScreenshotOutputType screenshotOutputType;
  private ScreenshotQuality screenshotQuality;
  private Integer frameRates;
  private Integer bitRate;
  private Boolean forceResolution;

  public ScreenshotStrategy getStrategy() {
    if (screenshotStrategy == null) {
      EngineProperties config = TestFramework.configuration()
        .get(EngineProperties.class);
      screenshotStrategy = config.getScreenshotStrategy();
    }
    return screenshotStrategy;
  }

  public ScreenshotOutputType getOutputType() {
    if (screenshotOutputType == null) {
      EngineProperties config = TestFramework.configuration()
        .get(EngineProperties.class);
      screenshotOutputType = config.getScreenshotOutputType();
    }
    return screenshotOutputType;
  }

  public ScreenshotQuality getScreenshotQuality() {
    if (screenshotQuality == null) {
      EngineProperties config = TestFramework.configuration()
        .get(EngineProperties.class);
      screenshotQuality = config.getScreenshotQuality();
    }
    return screenshotQuality;
  }

  public int getFrameRates() {
    if (frameRates == null) {
      EngineProperties config = TestFramework.configuration()
        .get(EngineProperties.class);
      frameRates = config.getScreenshotFps();
    }
    return frameRates;
  }

  public int getBitRate() {
    if (bitRate == null) {
      EngineProperties config = TestFramework.configuration()
        .get(EngineProperties.class);
      bitRate = config.getVideoBitRate();
    }
    return bitRate;
  }

  public Boolean isForceResolution() {
    if (forceResolution == null) {
      EngineProperties config = TestFramework.configuration()
        .get(EngineProperties.class);
      forceResolution = config.isForceResolution();
    }
    return forceResolution;
  }

  @After
  public void afterScenario(Scenario scenario) {
    ScreenshotOutputType type = getOutputType();
    if (type.equals(ScreenshotOutputType.IMAGE)) {
      ScreenshotService.attachScenarioScreenshot(getStrategy(), getScreenshotQuality(), scenario);
    }
  }

  @After(order = 100) // run EARLIER
  public void stopRecordingFirst(Scenario scenario) {
    ScreenshotOutputType type = getOutputType();
    if (type.equals(ScreenshotOutputType.VIDEO)) {
      ScreenshotService.attachRecording(getStrategy(), scenario);
    }
  }

  @AfterStep
  public void afterStep(Scenario scenario) {
    ScreenshotOutputType type = getOutputType();
    if (type.equals(ScreenshotOutputType.IMAGE)) {
      ScreenshotService.attachStepScreenshot(getStrategy(), getScreenshotQuality(), scenario);
    }
  }

  @BeforeStep
  public void beforeStep(Scenario scenario) {
    ScreenshotOutputType type = getOutputType();
    if (type.equals(ScreenshotOutputType.VIDEO)) {
      try {
        Actor actor = ActorManager.currentActor();
        if (actor != null) {
          ScreenshotService.startRecording(actor, getFrameRates(), isForceResolution(), getBitRate(), scenario);
        }
      } catch (Exception ignored) {

      }
    }
  }
}
