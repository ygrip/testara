package io.github.ygrip.testara.ui.support;

import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_COLOR;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_AREA;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

import org.awaitility.Awaitility;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.observation.Capture;
import lombok.extern.log4j.Log4j2;

/**
 * <p>A lightweight, asynchronous screen recording utility designed for UI test automation.
 * Inspired by :
 * <a href="https://anivaz.medium.com/building-a-lightweight-screen-recorder-for-selenium-tests-no-dependencies-no-headaches-116cf1884ccf">
 * Building a Lightweight Screen Recorder for Selenium Tests: No Dependencies, No Headaches
 * </a>
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Non-blocking screenshot capture using a scheduled executor</li>
 *   <li>Asynchronous video encoding using FFmpeg (JavaCV)</li>
 *   <li>In-memory streaming pipeline (no temporary files)</li>
 *   <li>Bounded frame queue with backpressure (frame dropping)</li>
 *   <li>Browser-compatible MP4 output (H264 + YUV420P)</li>
 *   <li>Detailed performance metrics logging</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 *   ScreenRecorder.instance()
 *       .withActor(actor)
 *       .startRecording("target/video/test", 30);
 *
 *   File video = ScreenRecorder.instance()
 *       .stopRecordingAsync()
 *       .join();
 * </pre>
 *
 * <h2>Important Notes</h2>
 * <ul>
 *   <li>Recording is thread-confined via ThreadLocal (safe for parallel tests)</li>
 *   <li>Frame dropping occurs when system is under load (prevents blocking)</li>
 *   <li>Call {@code stopRecordingAsync().join()} before accessing output</li>
 * </ul>
 * <p>
 */
@Log4j2
public final class ScreenRecorder {

  private static final ThreadLocal<ScreenRecorder> INSTANCES = new ThreadLocal<>();
  private static final int DEFAULT_FPS = 10;
  private static final int MAX_WIDTH = 1280;
  private static final int MAX_HEIGHT = 720;

  private final OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
  private final AtomicBoolean capturing = new AtomicBoolean(false);
  private final AtomicBoolean captureStopped = new AtomicBoolean(false);
  public String outputPath;
  private Actor actor;
  private int frameRate = DEFAULT_FPS;
  private ScheduledExecutorService captureExecutor;
  private ExecutorService encoderExecutor;
  private BlockingQueue<Frame> frameQueue;
  private volatile boolean recording = false;
  private RecordingMetrics metrics;
  private boolean forceResolution = true;
  private int bitRate = 4;

  private ScreenRecorder() {
  }

  public static ScreenRecorder instance() {
    ScreenRecorder inst = INSTANCES.get();
    if (inst == null) {
      inst = new ScreenRecorder();
      INSTANCES.set(inst);
    }
    return inst;
  }

  public String outputPath() {
    return this.outputPath;
  }

  public ScreenRecorder withActor(Actor actor) {
    this.actor = actor;
    return this;
  }

  public ScreenRecorder forceResolution(boolean compressed) {
    this.forceResolution = compressed;
    return this;
  }

  public ScreenRecorder bitRate(int bitRate) {
    this.bitRate = Math.max(bitRate, 1);
    return this;
  }

  private Actor getActor() {
    try {
      return actor != null ? actor : ActorManager.currentActor();
    } catch (Exception e) {
      return null;
    }
  }

  // =========================
  // START
  // =========================
  public void startRecording(String path, int fps) throws FrameRecorder.Exception {
    if (recording) {
      log.warn("Recording already running, restarting...");
      stopRecordingAsync().join();
    }

    this.frameRate = fps > 0 ? fps : DEFAULT_FPS;
    this.outputPath = resolvePath(path);

    this.frameQueue = new ArrayBlockingQueue<>(frameRate * 2);
    this.metrics = new RecordingMetrics();
    this.recording = true;

    startEncoder();
    startCapture();

    log.info("Recording started | path={} fps={} buffer={}", outputPath, frameRate, frameQueue.remainingCapacity());
  }

  private String resolvePath(String path) {
    if (!path.endsWith(".mp4"))
      path += ".mp4";
    if (!path.startsWith("./") && !path.startsWith("/"))
      path = "./" + path;
    return path;
  }

  // =========================
  // CAPTURE
  // =========================
  private void startCapture() {
    captureExecutor =
      Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("recorder-capture-%d")
        .setDaemon(true)
        .build());

    long interval = 1000L / frameRate; // avoid overload

    captureExecutor.scheduleAtFixedRate(
      () -> {
        if (!recording)
          return;

        // prevent overlapping capture
        if (!capturing.compareAndSet(false, true))
          return;

        long start = System.nanoTime();

        try {
          Actor actor = getActor();
          if (actor == null)
            return;

          byte[] png = actor.observe(Capture.page()
            .visibleOnViewPort());

          metrics.markScreenshot(System.nanoTime() - start);

          Mat mat = opencv_imgcodecs.imdecode(new Mat(new BytePointer(png)), IMREAD_COLOR);
          if (mat == null)
            return;

          Mat scaled = forceResolution ? scaleProportionally(mat, MAX_WIDTH, MAX_HEIGHT) : mat;
          Frame frame = converter.convert(scaled);

          if (!frameQueue.offer(frame)) {
            metrics.markDrop();
          } else {
            metrics.markCapture(System.nanoTime() - start);
          }

        } catch (Exception e) {
          log.warn("Capture error: {}", e.getMessage());
        } finally {
          capturing.set(false);
        }
      }, 0, interval, TimeUnit.MILLISECONDS
    );
  }

  private void startEncoder() {
    final int bitRate = this.bitRate * 1024 * 1024;

    encoderExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("recorder-encoder-%d")
      .build());

    final String outputFile = Paths.get(outputPath)
      .toAbsolutePath()
      .toString();
    boolean hasX264 = avcodec.avcodec_find_encoder_by_name("libx264") != null;

    encoderExecutor.submit(() -> {
      FFmpegFrameRecorder recorder = null;
      boolean started = false;

      try {
        Frame first = waitFirstFrame();

        // ensure directory exists
        Path parent = Paths.get(outputFile)
          .getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }

        long startTime = System.nanoTime();

        recorder = new FFmpegFrameRecorder(outputFile, first.imageWidth, first.imageHeight);

        recorder.setFormat("mp4");
        recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        recorder.setFrameRate(frameRate);
        recorder.setVideoBitrate(bitRate);

        if (hasX264) {
          recorder.setVideoCodecName("libx264");

          recorder.setVideoOption("preset", "ultrafast");
          recorder.setVideoOption("crf", "28");
          recorder.setVideoOption("profile", "baseline");
          recorder.setVideoOption("level", "3.0");

        } else {
          recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        }

        recorder.start();
        started = true;

        // first frame
        recorder.setTimestamp(0);
        recorder.record(first);

        while (true) {
          Frame frame = frameQueue.poll(200, TimeUnit.MILLISECONDS);

          if (frame != null) {
            long ts = (System.nanoTime() - startTime) / 1000;
            // enforce monotonic increase
            long lastTs = recorder.getTimestamp();
            if (ts <= lastTs) {
              ts = lastTs + 1;
            }
            recorder.setTimestamp(ts);

            long start = System.nanoTime();
            recorder.record(frame);
            metrics.markEncode(System.nanoTime() - start);
            continue;
          }

          // exit condition ONLY when:
          // - capture fully stopped
          // - queue fully drained
          if (captureStopped.get() && frameQueue.isEmpty()) {
            break;
          }
        }

        log.debug("Encoding thread finished");

      } catch (Exception e) {
        log.error("Encoder error", e);
      } finally {
        if (recorder != null) {
          try {
            if (started) {
              recorder.stop();
            }
          } catch (Exception e) {
            log.warn("Error stopping recorder", e);
          }

          try {
            recorder.release();
          } catch (Exception e) {
            log.warn("Error releasing recorder", e);
          }
        }
      }
    });
  }

  private Frame waitFirstFrame() throws InterruptedException {
    long start = System.currentTimeMillis();

    while (true) {
      Frame frame = frameQueue.poll(200, TimeUnit.MILLISECONDS);

      if (frame != null)
        return frame;

      if (!recording && frameQueue.isEmpty()) {
        throw new IllegalStateException("No frames captured");
      }

      if (System.currentTimeMillis() - start > 5000) {
        throw new IllegalStateException("Timeout waiting first frame");
      }
    }
  }

  // =========================
  // SCALING
  // =========================
  private Mat scaleProportionally(Mat src, int maxWidth, int maxHeight) {
    int srcW = src.cols();
    int srcH = src.rows();

    // no scaling needed
    if (srcW <= maxWidth && srcH <= maxHeight) {
      return src.clone();
    }

    double scale = Math.min((double) maxWidth / srcW, (double) maxHeight / srcH);

    int targetW = Math.max(2, ((int) Math.round(srcW * scale)) & ~1);
    int targetH = Math.max(2, ((int) Math.round(srcH * scale)) & ~1);

    Mat resized = new Mat();

    // better for downscaling
    resize(src, resized, new Size(targetW, targetH), 0, 0, INTER_AREA);

    return resized;
  }

  private void captureFinalFrame() {
    try {
      long start = System.nanoTime();

      Actor actor = getActor();
      if (actor == null)
        return;

      byte[] png = actor.observe(Capture.page()
        .visibleOnViewPort());
      metrics.markScreenshot(System.nanoTime() - start);

      Mat mat = opencv_imgcodecs.imdecode(new Mat(new BytePointer(png)), IMREAD_COLOR);
      if (mat == null)
        return;

      Mat scaled = forceResolution ? scaleProportionally(mat, MAX_WIDTH, MAX_HEIGHT) : mat;
      Frame frame = converter.convert(scaled);

      if (!frameQueue.offer(frame)) {
        metrics.markDrop();
      } else {
        metrics.markCapture(System.nanoTime() - start);
      }

    } catch (Exception e) {
      log.debug("Final frame capture skipped: {}", e.getMessage());
    }
  }

  // =========================
  // STOP
  // =========================
  public CompletableFuture<File> stopRecordingAsync() {
    recording = false;

    // STOP scheduling new captures immediately
    if (captureExecutor != null) {
      captureExecutor.shutdownNow();
      try {
        captureExecutor.awaitTermination(2, TimeUnit.SECONDS);
      } catch (InterruptedException ignored) {

      }
    }
    captureStopped.set(true);

    // Force one last frame BEFORE driver disappears, add proportional delay
    Awaitility.await()
      .pollInSameThread()
      .pollDelay(200, TimeUnit.MILLISECONDS)
      .atMost(201, TimeUnit.MILLISECONDS)
      .until(() -> true);
    captureFinalFrame();

    return CompletableFuture.supplyAsync(() -> {
      try {
        // wait encoder to fully drain queue
        if (encoderExecutor != null) {
          encoderExecutor.shutdown();
          encoderExecutor.awaitTermination(20, TimeUnit.SECONDS);
        }

        File file = new File(outputPath);

        if (!file.exists() || file.length() < 1000) {
          throw new RuntimeException("Invalid video output: " + file.getAbsolutePath());
        }

        log.info(metrics.summary(file.length()));

        return file;

      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  // =========================
  // METRICS
  // =========================
  static final class RecordingMetrics {
    final long startNs = System.nanoTime();

    final LongAdder screenshotFrames = new LongAdder();
    final LongAdder capturedFrames = new LongAdder();
    final LongAdder droppedFrames = new LongAdder();
    final LongAdder encodedFrames = new LongAdder();

    final LongAdder screenshotTimeNs = new LongAdder();
    final LongAdder captureTimeNs = new LongAdder();
    final LongAdder encodeTimeNs = new LongAdder();

    void markCapture(long ns) {
      capturedFrames.increment();
      captureTimeNs.add(ns);
    }

    long getFrameDurationInMs() {
      long shot = screenshotFrames.sum();
      double avgShot = shot == 0 ? 0 : (screenshotTimeNs.sum() / 1_000.0) / shot;
      return (long) avgShot;
    }

    long getCapturedFrames() {
      return capturedFrames.sum();
    }

    void markScreenshot(long ns) {
      screenshotFrames.increment();
      screenshotTimeNs.add(ns);
    }

    void markDrop() {
      droppedFrames.increment();
    }

    void markEncode(long ns) {
      encodedFrames.increment();
      encodeTimeNs.add(ns);
    }

    String summary(long fileBytes) {
      long totalNs = System.nanoTime() - startNs;
      double sec = totalNs / 1_000_000_000.0;

      long shot = screenshotFrames.sum();
      long cap = capturedFrames.sum();
      long enc = encodedFrames.sum();
      long drop = droppedFrames.sum();

      double avgCap = cap == 0 ? 0 : (captureTimeNs.sum() / 1_000_000.0) / cap;
      double avgShot = shot == 0 ? 0 : (screenshotTimeNs.sum() / 1_000_000.0) / shot;
      double avgEnc = enc == 0 ? 0 : (encodeTimeNs.sum() / 1_000_000.0) / enc;

      double fps = enc / Math.max(sec, 0.001);

      return String.format(
        "ScreenRecorder Summary | duration=%.2fs, screenshot=%d, captured=%d, encoded=%d, dropped=%d, fps=%.2f, avgScreenshot=%.2fms, avgCapture=%.2fms, avgEncode=%.2fms, size=%.2fMB",
        sec,
        shot,
        cap,
        enc,
        drop,
        fps,
        avgShot,
        avgCap,
        avgEnc,
        fileBytes / (1024.0 * 1024.0)
      );
    }
  }
}
