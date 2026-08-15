package io.github.ygrip.testara.reporter.support;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import io.github.ygrip.testara.reporter.cucumber.Embedding;

/** Resolves Testara's lightweight Cucumber screenshot reference attachments. */
public final class ScreenshotReferenceResolver {
  public static final String MIME_TYPE = "application/vnd.testara.screenshot-reference";
  private static final Path DEFAULT_DIRECTORY = Path.of("target", "testara-screenshots");

  private final Path directory;

  public ScreenshotReferenceResolver() {
    this(DEFAULT_DIRECTORY);
  }

  ScreenshotReferenceResolver(Path directory) {
    this.directory = directory.toAbsolutePath().normalize();
  }

  public Optional<ResolvedScreenshot> resolve(Embedding embedding) {
    if (embedding == null || !MIME_TYPE.equalsIgnoreCase(embedding.getStoredMimeType())) {
      return Optional.empty();
    }

    try {
      String id = embedding.getDecodedData().trim();
      UUID.fromString(id);
      Path screenshot = directory.resolve(id + ".image").normalize();
      if (!screenshot.startsWith(directory) || !Files.isRegularFile(screenshot)) {
        return Optional.empty();
      }
      byte[] bytes = Files.readAllBytes(screenshot);
      String mimeType = detectMimeType(bytes);
      if (mimeType == null) {
        return Optional.empty();
      }
      String name = embedding.getName() == null || embedding.getName().isBlank()
        ? id
        : embedding.getName();
      return Optional.of(new ResolvedScreenshot(name, mimeType, bytes));
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  private static String detectMimeType(byte[] bytes) {
    if (bytes == null || bytes.length < 4) {
      return null;
    }
    if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
      return "image/jpeg";
    }
    if ((bytes[0] & 0xff) == 0x89
      && bytes[1] == 0x50
      && bytes[2] == 0x4e
      && bytes[3] == 0x47) {
      return "image/png";
    }
    if (bytes.length >= 12
      && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
      && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
      return "image/webp";
    }
    return null;
  }

  public record ResolvedScreenshot(String name, String mimeType, byte[] bytes) {}
}
