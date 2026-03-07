package io.github.ygrip.testara.reporter.reader;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Arrays;

public class TokenReplacingReader extends Reader {
  private final String tokenStartMarker;
  private final String tokenEndMarker;
  private final char[] tokenStartMarkerChars;
  private final char[] tokenEndMarkerChars;
  private final char[] tmpTokenStartMarkerChars;
  private final char[] tmpTokenEndMarkerChars;
  private final PushbackReader pushbackReader;
  private final TokenResolver tokenResolver;
  private final StringBuilder tokenBuffer = new StringBuilder();
  private String resolvedToken = null;
  private int resolvedTokenIndex = 0;

  public TokenReplacingReader(final TokenResolver resolver, final Reader source) {
    if (resolver == null) {
      throw new IllegalArgumentException("Token resolver is null");
    }

    String tokenStartMarker = resolver.getStartMarker();
    String tokenEndMarker = resolver.getEndMarker();

    if ((tokenStartMarker == null || tokenStartMarker.length() < 1) || (tokenEndMarker == null
        || tokenEndMarker.length() < 1)) {
      throw new IllegalArgumentException("Token start / end marker is null or empty");
    }

    this.tokenStartMarker = tokenStartMarker;
    this.tokenEndMarker = tokenEndMarker;
    tokenStartMarkerChars = tokenStartMarker.toCharArray();
    tokenEndMarkerChars = tokenEndMarker.toCharArray();
    tmpTokenStartMarkerChars = new char[tokenStartMarker.length()];
    tmpTokenEndMarkerChars = new char[tokenEndMarker.length()];
    pushbackReader =
        new PushbackReader(source, Math.max(tokenStartMarker.length(), tokenEndMarker.length()));
    tokenResolver = resolver;
  }

  @Override
  public int read() throws IOException {
    if (resolvedToken != null && !resolvedToken.trim().isEmpty()) {
      if (resolvedTokenIndex < resolvedToken.length()) {
        return resolvedToken.charAt(resolvedTokenIndex++);
      }

      if (resolvedTokenIndex == resolvedToken.length()) {
        resolvedToken = null;
        resolvedTokenIndex = 0;
      }
    }else {
      resolvedTokenIndex = 0;
    }

    // read proper number of chars into a temp. char array in order to find token start marker
    int countValidChars = readChars(tmpTokenStartMarkerChars);

    if (!Arrays.equals(tmpTokenStartMarkerChars, tokenStartMarkerChars)) {
      if (countValidChars > 0) {
        pushbackReader.unread(tmpTokenStartMarkerChars, 0, countValidChars);
      }

      return pushbackReader.read();
    }

    // found start of token, read proper number of chars into a temp. char array in order to find token end marker
    boolean endOfSource = false;
    tokenBuffer.delete(0, tokenBuffer.length());
    countValidChars = readChars(tmpTokenEndMarkerChars);

    while (!Arrays.equals(tmpTokenEndMarkerChars, tokenEndMarkerChars)) {
      if (countValidChars == -1) {
        // end of source and no token end marker was found
        endOfSource = true;

        break;
      }

      tokenBuffer.append(tmpTokenEndMarkerChars[0]);

      pushbackReader.unread(tmpTokenEndMarkerChars, 0, countValidChars);
      if (pushbackReader.read() == -1) {
        // end of source and no token end marker was found
        endOfSource = true;

        break;
      }

      countValidChars = readChars(tmpTokenEndMarkerChars);
    }

    if (endOfSource) {
      resolvedToken = tokenStartMarker + tokenBuffer;
    } else {
      // try to resolve token
      resolvedToken = tokenResolver.resolveToken(tokenBuffer.toString());
      if (resolvedToken == null || resolvedToken.trim().isEmpty()) {
        // token was not resolved
        resolvedToken = tokenStartMarker + tokenBuffer + tokenEndMarker;
      }
    }

    return resolvedToken.charAt(resolvedTokenIndex++);
  }

  private int readChars(final char[] tmpChars) throws IOException {
    int countValidChars = -1;
    final int length = tmpChars.length;
    int data = pushbackReader.read();

    for (int i = 0; i < length; i++) {
      if (data != -1) {
        tmpChars[i] = (char) data;
        countValidChars = i + 1;
        if (i + 1 < length) {
          data = pushbackReader.read();
        }
      } else {
        // reset to java default value for char
        tmpChars[i] = '\u0000';
      }
    }

    return countValidChars;
  }

  @Override
  public int read(final char[] cbuf) throws IOException {
    return read(cbuf, 0, cbuf.length);
  }

  @Override
  public int read(final char[] cbuf, final int off, final int len) throws IOException {
    int charsRead = 0;
    for (int i = 0; i < len; i++) {
      final int nextChar = read();
      if (nextChar == -1) {
        charsRead = i;
        if (charsRead == 0) {
          charsRead = -1;
        }

        break;
      } else {
        charsRead = i + 1;
      }

      cbuf[off + i] = (char) nextChar;
    }

    return charsRead;
  }

  @Override
  public void close() throws IOException {
    pushbackReader.close();
  }

  @Override
  public boolean ready() throws IOException {
    return pushbackReader.ready();
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  @Override
  public int read(final CharBuffer target) {
    throw new UnsupportedOperationException("Method int read(CharBuffer target) is not supported");
  }

  @Override
  public long skip(final long n) {
    throw new UnsupportedOperationException("Method long skip(long n) is not supported");
  }

  @Override
  public void mark(final int readAheadLimit) {
    throw new UnsupportedOperationException("Method void mark(int readAheadLimit) is not supported");
  }

  @Override
  public void reset() {
    throw new UnsupportedOperationException("Method void reset() is not supported");
  }
}
