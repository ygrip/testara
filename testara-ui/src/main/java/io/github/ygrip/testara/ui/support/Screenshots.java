package io.github.ygrip.testara.ui.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import io.github.ygrip.testara.ui.model.ScreenshotQuality;

/**
 * Utility for working with screenshot byte arrays produced by
 * {@link io.github.ygrip.testara.ui.observation.Capture}.
 */
public final class Screenshots {
  private static final int OPTIMIZATION_THRESHOLD_BYTES = 256 * 1024;
  private static final String PNG_MIME_TYPE = "image/png";
  private static final String JPEG_MIME_TYPE = "image/jpeg";

  private Screenshots() {}

  /**
   * Optimizes a screenshot using the requested quality preset.
   * Small screenshots are left untouched to avoid unnecessary CPU work.
   * If optimization cannot reduce the payload safely, the original PNG is returned.
   *
   * @param screenshot raw PNG screenshot bytes
   * @param quality requested quality preset, defaults to STANDARD when null
   * @return optimized screenshot bytes and matching MIME type
   */
  public static OptimizedScreenshot optimize(byte[] screenshot, ScreenshotQuality quality) {
    if (screenshot == null || screenshot.length == 0 || screenshot.length <= OPTIMIZATION_THRESHOLD_BYTES) {
      return new OptimizedScreenshot(screenshot, PNG_MIME_TYPE);
    }

    ScreenshotQuality selectedQuality = quality == null ? ScreenshotQuality.STANDARD : quality;
    try {
      BufferedImage source = toBufferedImage(screenshot);
      BufferedImage image = resizeForQuality(source, selectedQuality);
      byte[] jpeg = encodeJpeg(image, selectedQuality.jpegQuality());
      if (jpeg.length > 0 && jpeg.length < screenshot.length) {
        return new OptimizedScreenshot(jpeg, JPEG_MIME_TYPE);
      }
    } catch (Exception ignored) {
      // Preserve screenshot attachment even when optimization is unsupported or fails.
    }
    return new OptimizedScreenshot(screenshot, PNG_MIME_TYPE);
  }

  private static BufferedImage resizeForQuality(BufferedImage source, ScreenshotQuality quality) {
    int width = source.getWidth();
    int height = source.getHeight();
    int longEdge = Math.max(width, height);
    double scale = longEdge > quality.maxLongEdge()
      ? quality.maxLongEdge() / (double) longEdge
      : 1.0d;
    int targetWidth = Math.max(1, (int) Math.round(width * scale));
    int targetHeight = Math.max(1, (int) Math.round(height * scale));

    BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = target.createGraphics();
    try {
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, targetWidth, targetHeight);
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    if (!writers.hasNext()) {
      throw new IOException("No JPEG image writer is available");
    }

    ImageWriter writer = writers.next();
    try (ByteArrayOutputStream output = new ByteArrayOutputStream();
         ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
      writer.setOutput(imageOutput);
      ImageWriteParam parameters = writer.getDefaultWriteParam();
      if (parameters.canWriteCompressed()) {
        parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parameters.setCompressionQuality(quality);
      }
      writer.write(null, new IIOImage(image, null, null), parameters);
      imageOutput.flush();
      return output.toByteArray();
    } finally {
      writer.dispose();
    }
  }

  /**
   * Convert raw PNG bytes to a {@link BufferedImage}.
   *
   * @param png the PNG screenshot bytes
   * @return a {@link BufferedImage}, never {@code null}
   * @throws IOException if the bytes cannot be decoded
   */
  public static BufferedImage toBufferedImage(byte[] png) throws IOException {
    if (png == null || png.length == 0) {
      throw new IOException("Screenshot byte array is null or empty");
    }
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
    if (image == null) {
      throw new IOException("Failed to decode PNG bytes into a BufferedImage");
    }
    return image;
  }

  /**
   * Save raw PNG bytes to the given full path.
   * Parent directories are created automatically.
   *
   * @param png      the PNG screenshot bytes
   * @param fullPath absolute or relative path including the file name (e.g. {@code "/tmp/shots/login.png"})
   * @return the resolved {@link Path} that was written
   * @throws IOException if the file cannot be written
   */
  public static Path save(byte[] png, String fullPath) throws IOException {
    return save(png, Path.of(fullPath));
  }

  /**
   * Save raw PNG bytes to the given {@link Path}.
   * Parent directories are created automatically.
   *
   * @param png  the PNG screenshot bytes
   * @param path target path including the file name
   * @return the resolved {@link Path} that was written
   * @throws IOException if the file cannot be written
   */
  public static Path save(byte[] png, Path path) throws IOException {
    if (png == null || png.length == 0) {
      throw new IOException("Screenshot byte array is null or empty");
    }
    Path resolved = path.toAbsolutePath().normalize();
    Files.createDirectories(resolved.getParent());
    Files.write(resolved, png);
    return resolved;
  }

  public record OptimizedScreenshot(byte[] bytes, String mimeType) {}
}
