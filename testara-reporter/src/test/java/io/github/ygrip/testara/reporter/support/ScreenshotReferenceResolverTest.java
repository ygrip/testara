package io.github.ygrip.testara.reporter.support;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ygrip.testara.reporter.cucumber.Embedding;

class ScreenshotReferenceResolverTest {
  @TempDir Path tempDir;

  @Test
  void resolvesOnlyUuidReferencesFromTheConfiguredScreenshotDirectory() throws Exception {
    ScreenshotReferenceResolver resolver = new ScreenshotReferenceResolver(tempDir);
    String id = UUID.randomUUID().toString();
    byte[] png = png();
    Files.write(tempDir.resolve(id + ".image"), png);
    Embedding embedding = reference(id, "after-step");

    var resolved = resolver.resolve(embedding).orElseThrow();

    assertEquals("after-step", resolved.name());
    assertEquals("image/png", resolved.mimeType());
    assertArrayEquals(png, resolved.bytes());
    assertTrue(resolver.resolve(reference("../../pom.xml", "invalid")).isEmpty());
  }

  private static Embedding reference(String id, String name) {
    String encoded = Base64.getEncoder().encodeToString(id.getBytes(StandardCharsets.UTF_8));
    return new Embedding(ScreenshotReferenceResolver.MIME_TYPE, encoded, name);
  }

  private static byte[] png() throws Exception {
    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }
}
