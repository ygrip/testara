package io.github.ygrip.testara.ui.support;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ImagingOpException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
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
   * Optimizes a PNG screenshot using the requested quality preset.
   *
   * @param screenshot raw PNG screenshot bytes
   * @param quality requested quality preset, defaults to STANDARD when null
   * @return optimized screenshot bytes and matching MIME type
   */
  public static OptimizedScreenshot optimize(byte[] screenshot, ScreenshotQuality quality) {
    return optimize(screenshot, PNG_MIME_TYPE, quality);
  }

  /**
   * Optimizes screenshot bytes without reprocessing an already-small JPEG.
   * PNG payloads above the threshold are decoded once, resized when needed,
   * and encoded once. Native JPEG captures are returned directly when they
   * already fit the requested resolution cap.
   */
  public static OptimizedScreenshot optimize(byte[] screenshot, String mimeType, ScreenshotQuality quality) {
    String sourceMimeType = normalizeMimeType(mimeType);
    if (screenshot == null || screenshot.length == 0) {
      return new OptimizedScreenshot(screenshot, sourceMimeType);
    }

    ScreenshotQuality selectedQuality = quality == null ? ScreenshotQuality.STANDARD : quality;
    boolean jpegSource = JPEG_MIME_TYPE.equals(sourceMimeType);
    boolean inspectSize = jpegSource || screenshot.length <= OPTIMIZATION_THRESHOLD_BYTES;
    boolean oversized = false;

    try {
      if (inspectSize) {
        oversized = exceedsMaxLongEdge(screenshot, selectedQuality.maxLongEdge());
        if (!oversized) {
          return new OptimizedScreenshot(screenshot, sourceMimeType);
        }
      }

      BufferedImage source = toBufferedImage(screenshot);
      oversized = Math.max(source.getWidth(), source.getHeight()) > selectedQuality.maxLongEdge();
      BufferedImage image = resizeForQuality(source, selectedQuality);
      byte[] jpeg = encodeJpeg(image, selectedQuality.jpegQuality());
      if (jpeg.length > 0 && (oversized || jpeg.length < screenshot.length)) {
        return new OptimizedScreenshot(jpeg, JPEG_MIME_TYPE);
      }
    } catch (Exception ignored) {
      // Preserve the screenshot even when optimization is unsupported or fails.
    }
    return new OptimizedScreenshot(screenshot, sourceMimeType);
  }

  private static String normalizeMimeType(String mimeType) {
    if (mimeType == null || mimeType.isBlank()) {
      return PNG_MIME_TYPE;
    }
    return "image/jpg".equalsIgnoreCase(mimeType) ? JPEG_MIME_TYPE : mimeType.toLowerCase();
  }

  private static boolean exceedsMaxLongEdge(byte[] screenshot, int maxLongEdge) throws IOException {
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(screenshot))) {
      if (input == null) {
        throw new IOException("Failed to open screenshot image stream");
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw new IOException("No image reader is available for screenshot bytes");
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        return Math.max(reader.getWidth(0), reader.getHeight(0)) > maxLongEdge;
      } finally {
        reader.dispose();
      }
    }
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

    if (targetWidth == width && targetHeight == height && !source.getColorModel().hasAlpha()) {
      return source;
    }

    BufferedImage rgbSource = toRgb(source);
    if (targetWidth == width && targetHeight == height) {
      return rgbSource;
    }

    BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
    AffineTransform transform = AffineTransform.getScaleInstance(
      targetWidth / (double) width,
      targetHeight / (double) height
    );
    try {
      return new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR).filter(rgbSource, target);
    } catch (ImagingOpException ignored) {
      return resizeNearestNeighbor(rgbSource, targetWidth, targetHeight);
    }
  }

  private static BufferedImage toRgb(BufferedImage source) {
    if (source.getType() == BufferedImage.TYPE_INT_RGB) {
      return source;
    }

    int width = source.getWidth();
    int height = source.getHeight();
    BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    int[] row = new int[width];
    for (int y = 0; y < height; y++) {
      source.getRGB(0, y, width, 1, row, 0, width);
      target.setRGB(0, y, width, 1, row, 0, width);
    }
    return target;
  }

  private static BufferedImage resizeNearestNeighbor(BufferedImage source, int targetWidth, int targetHeight) {
    int sourceWidth = source.getWidth();
    int sourceHeight = source.getHeight();
    BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
    int[] sourceRow = new int[sourceWidth];
    int[] targetRow = new int[targetWidth];
    int previousSourceY = -1;

    for (int y = 0; y < targetHeight; y++) {
      int sourceY = Math.min(sourceHeight - 1, (int) ((long) y * sourceHeight / targetHeight));
      if (sourceY != previousSourceY) {
        source.getRGB(0, sourceY, sourceWidth, 1, sourceRow, 0, sourceWidth);
        previousSourceY = sourceY;
      }
      for (int x = 0; x < targetWidth; x++) {
        int sourceX = Math.min(sourceWidth - 1, (int) ((long) x * sourceWidth / targetWidth));
        targetRow[x] = sourceRow[sourceX];
      }
      target.setRGB(0, y, targetWidth, 1, targetRow, 0, targetWidth);
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
   * Convert raw screenshot bytes to a {@link BufferedImage}.
   *
   * @param screenshot screenshot bytes supported by ImageIO
   * @return a {@link BufferedImage}, never {@code null}
   * @throws IOException if the bytes cannot be decoded
   */
  public static BufferedImage toBufferedImage(byte[] screenshot) throws IOException {
    if (screenshot == null || screenshot.length == 0) {
      throw new IOException("Screenshot byte array is null or empty");
    }
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(screenshot));
    if (image == null) {
      throw new IOException("Failed to decode screenshot bytes into a BufferedImage");
    }
    return image;
  }

  /**
   * Save raw PNG bytes to the given full path.
   * Parent directories are created automatically.
   */
  public static Path save(byte[] png, String fullPath) throws IOException {
    return save(png, Path.of(fullPath));
  }

  /**
   * Save raw PNG bytes to the given {@link Path}.
   * Parent directories are created automatically.
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
