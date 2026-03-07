package io.github.ygrip.testara.reporter.reader;

import java.io.IOException;

public abstract class TokenResolver {
  private final String startMarker;
  private final String endMarker;

  public TokenResolver(String startMarker, String endMarker) {
    this.startMarker = startMarker;
    this.endMarker = endMarker;
  }

  public abstract boolean hasMapping();

  public abstract TokenResolver addToken(String key, String value);

  public abstract String resolveToken(String token) throws IOException;

  public String getStartMarker() {
    return startMarker;
  }

  public String getEndMarker() {
    return endMarker;
  }
}
