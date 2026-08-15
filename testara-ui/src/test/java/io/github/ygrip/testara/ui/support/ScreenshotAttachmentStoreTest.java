package io.github.ygrip.testara.ui.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ygrip.testara.ui.model.CapturedScreenshot;
import io.github.ygrip.testara.ui.model.ScreenshotQuality;

class ScreenshotAttachmentStoreTest {
  @TempDir Path tempDir;

  @Test
  void defersOptimizationAndWritesResolutionCappedScreenshot() throws Exception {
    Queue<Runnable> queuedWork = new ArrayDeque<>();
    ScreenshotAttachmentStore store = new ScreenshotAttachmentStore(tempDir, queuedWork::add);
    byte[] original = solidPng(1600, 1000);

    ScreenshotAttachmentStore.Reference reference = store.store(
      "scenario-1",
      new CapturedScreenshot(original, "image/png"),
      ScreenshotQuality.STANDARD
    );

    Path stored = tempDir.resolve(reference.id() + ".image");
    assertEquals(1, queuedWork.size());
    assertFalse(Files.exists(stored));
    assertTrue(reference.id().matches("[0-9a-f-]{36}"));

    queuedWork.remove().run();
    store.await("scenario-1");

    assertTrue(Files.isRegularFile(stored));
    BufferedImage image = ImageIO.read(stored.toFile());
    assertEquals(1280, image.getWidth());
    assertEquals(800, image.getHeight());
  }

  private static byte[] solidPng(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        image.setRGB(x, y, 0x336699);
      }
    }
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }
}
