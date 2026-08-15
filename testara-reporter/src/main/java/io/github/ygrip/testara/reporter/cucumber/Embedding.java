package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.ygrip.testara.reporter.support.ScreenshotReferenceResolver;

public class Embedding implements Serializable {
  @JsonIgnore
  private static final String FILE_EXTENSION_PATTERN = "[a-z0-9]+";
  @JsonIgnore
  private static final String UNKNOWN_FILE_EXTENSION = "unknown";
  @JsonProperty("mime_type")
  private final String mimeType;
  private final String data;
  private final String name;
  private final String fileId;
  @JsonIgnore
  private transient volatile ScreenshotReferenceResolver.ResolvedScreenshot resolvedReference;
  @JsonIgnore
  private transient volatile boolean referenceResolutionAttempted;

  public Embedding(String mimeType, String data) {
    this(mimeType, data, null);
  }

  public Embedding(String mimeType, String data, String name) {
    this.mimeType = mimeType;
    this.data = data;
    this.name = name;
    this.fileId = "embedding_" + data.hashCode();
  }

  @JsonProperty("mime_type")
  public String getMimeType() {
    ScreenshotReferenceResolver.ResolvedScreenshot resolved = resolveReference();
    return resolved == null ? this.mimeType : resolved.mimeType();
  }

  public String getData() {
    ScreenshotReferenceResolver.ResolvedScreenshot resolved = resolveReference();
    return resolved == null ? this.data : Base64.getEncoder().encodeToString(resolved.bytes());
  }

  public String getName() {
    return this.name;
  }

  @JsonIgnore
  public String getStoredMimeType() {
    return this.mimeType;
  }

  @JsonIgnore
  public String getDecodedData() {
    return new String(Base64.getDecoder().decode(this.data.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
  }

  @JsonIgnore
  public String getFileName() {
    return this.fileId + "." + this.getExtension();
  }

  public String getFileId() {
    return this.fileId;
  }

  @JsonIgnore
  public String getExtension() {
    String mime = this.getMimeType();
    if (mime.contains("+")) {
      mime = mime.substring(0, mime.indexOf(43));
    }

    if (mime.contains(";")) {
      mime = mime.substring(0, mime.indexOf(59));
    }

    mime = mime.toLowerCase(Locale.ENGLISH).trim();
    byte var3 = -1;
    switch(mime.hashCode()) {
      case -1348221103:
        if (mime.equals("application/x-tar")) {
          var3 = 4;
        }
        break;
      case -879253829:
        if (mime.equals("image/url")) {
          var3 = 0;
        }
        break;
      case -366307023:
        if (mime.equals("application/vnd.ms-excel")) {
          var3 = 8;
        }
        break;
      case -43923783:
        if (mime.equals("application/gzip")) {
          var3 = 6;
        }
        break;
      case 302663708:
        if (mime.equals("application/ecmascript")) {
          var3 = 2;
        }
        break;
      case 817335912:
        if (mime.equals("text/plain")) {
          var3 = 1;
        }
        break;
      case 1423759679:
        if (mime.equals("application/x-bzip2")) {
          var3 = 5;
        }
        break;
      case 1440428940:
        if (mime.equals("application/javascript")) {
          var3 = 3;
        }
        break;
      case 1993842850:
        if (mime.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
          var3 = 7;
        }
    }

    switch(var3) {
      case 0:
        return "image";
      case 1:
        return "txt";
      case 2:
        return "es";
      case 3:
        return "js";
      case 4:
        return "tar";
      case 5:
        return "bz2";
      case 6:
        return "gz";
      case 7:
        return "xlsx";
      case 8:
        return "xls";
      default:
        String subtype;
        if (this.name != null && this.name.contains(".")) {
          subtype = this.name.substring(this.name.lastIndexOf(46) + 1);
          if (subtype.matches(FILE_EXTENSION_PATTERN)) {
            return subtype;
          }
        }

        if (mime.contains("/")) {
          subtype = mime.substring(mime.indexOf(47) + 1);
          if (subtype.matches(FILE_EXTENSION_PATTERN)) {
            return subtype;
          }
        }

        return UNKNOWN_FILE_EXTENSION;
    }
  }

  private ScreenshotReferenceResolver.ResolvedScreenshot resolveReference() {
    if (!ScreenshotReferenceResolver.MIME_TYPE.equalsIgnoreCase(this.mimeType)) {
      return null;
    }
    if (!referenceResolutionAttempted) {
      synchronized (this) {
        if (!referenceResolutionAttempted) {
          resolvedReference = new ScreenshotReferenceResolver().resolve(this).orElse(null);
          referenceResolutionAttempted = true;
        }
      }
    }
    return resolvedReference;
  }
}
