package io.github.ygrip.testara.reporter.branding;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class BrandingLogoResolver {
  private static final Logger LOG = Logger.getLogger(BrandingLogoResolver.class.getName());
  private static final long MAX_EMBEDDED_LOGO_BYTES = 1024L * 1024L;
  private static final Map<String, String> MEDIA_TYPES = Map.of(
    "png", "image/png",
    "jpg", "image/jpeg",
    "jpeg", "image/jpeg",
    "gif", "image/gif"
  );

  public String resolve(String source) {
    if (source == null || source.isBlank()) {
      return null;
    }

    String value = source.trim();
    try {
      if (value.startsWith("https://") || value.startsWith("cid:")) {
        return value;
      }
      if (value.startsWith("data:image/")) {
        return isSupportedDataUri(value) ? value : warnAndIgnore("Unsupported data URI logo format: " + value);
      }
      if (value.startsWith("classpath:")) {
        return resolveClasspath(value.substring("classpath:".length()));
      }
      if (value.startsWith("file:")) {
        return resolveFile(Path.of(URI.create(value)));
      }
      return resolveFile(Path.of(value));
    } catch (Exception exception) {
      LOG.warning("Unable to resolve report organization logo '" + value + "': " + exception.getMessage());
      return null;
    }
  }

  private String resolveClasspath(String location) throws IOException {
    String resource = location.startsWith("/") ? location.substring(1) : location;
    try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
      if (input == null) {
        return warnAndIgnore("Report organization logo classpath resource was not found: " + location);
      }
      byte[] bytes = input.readNBytes((int) MAX_EMBEDDED_LOGO_BYTES + 1);
      return embed(bytes, resource);
    }
  }

  private String resolveFile(Path path) throws IOException {
    if (!Files.isRegularFile(path)) {
      return warnAndIgnore("Report organization logo file was not found: " + path);
    }
    long size = Files.size(path);
    if (size > MAX_EMBEDDED_LOGO_BYTES) {
      return warnAndIgnore("Report organization logo is larger than 1 MiB and will not be embedded: " + path);
    }
    return embed(Files.readAllBytes(path), path.getFileName().toString());
  }

  private String embed(byte[] bytes, String name) {
    if (bytes.length > MAX_EMBEDDED_LOGO_BYTES) {
      return warnAndIgnore("Report organization logo is larger than 1 MiB and will not be embedded: " + name);
    }
    String mediaType = mediaType(name);
    if (mediaType == null) {
      return warnAndIgnore("Unsupported report organization logo type: " + name + ". Use PNG, JPEG, or GIF.");
    }
    return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(bytes);
  }

  private String mediaType(String name) {
    int dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length() - 1) {
      return null;
    }
    return MEDIA_TYPES.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
  }

  private boolean isSupportedDataUri(String value) {
    return value.startsWith("data:image/png;base64,")
      || value.startsWith("data:image/jpeg;base64,")
      || value.startsWith("data:image/gif;base64,");
  }

  private String warnAndIgnore(String message) {
    LOG.warning(message);
    return null;
  }
}
