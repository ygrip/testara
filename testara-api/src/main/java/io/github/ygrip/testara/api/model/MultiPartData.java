package io.github.ygrip.testara.api.model;

import lombok.Data;

import java.util.Map;

@Data
public class MultiPartData {
  private Object content;
  private String controlName;
  private String mimeType;
  private String charset;
  private String fileName;
  private Map<String, String> headers;
}
