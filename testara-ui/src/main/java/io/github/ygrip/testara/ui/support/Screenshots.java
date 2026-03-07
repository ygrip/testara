package io.github.ygrip.testara.ui.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/**
 * Utility for working with screenshot byte arrays produced by
 * {@link io.github.ygrip.testara.ui.observation.Capture}.
 */
public final class Screenshots {

  private Screenshots() {}

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
}
