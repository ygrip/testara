package io.github.ygrip.testara.reporter.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.ygrip.testara.reporter.reader.TokenResolver;

public class PatternFinder {

  public static Map<String, String> createTokenMapping(TokenResolver resolver,
      String input,
      String output) {
    Map<String, String> result = new HashMap<>();
    List<Token> tokens = new ArrayList<>();
    if (input.contains(resolver.getStartMarker()) && input.contains(resolver.getEndMarker())) {
      char[] START_MARKERS = resolver.getStartMarker().toCharArray();
      char[] END_MARKERS = resolver.getEndMarker().toCharArray();
      int START_IDX = 0;
      int END_IDX = 0;
      StringBuilder identifier = new StringBuilder();
      boolean isFound = false;
      int foundAtIndex = 0;
      for (int i = 0; i < input.length(); i++) {
        if (START_IDX > START_MARKERS.length - 1) {
          START_IDX = 0;
        }
        if (END_IDX > END_MARKERS.length - 1) {
          END_IDX = 0;
        }
        if (input.charAt(i) == START_MARKERS[START_IDX]) {
          START_IDX++;
          if (START_IDX == START_MARKERS.length) {
            foundAtIndex = i;
            isFound = true;
            continue;
          }
        } else if (input.charAt(i) == END_MARKERS[END_IDX]) {
          END_IDX++;
        }
        if (isFound && END_IDX == 0) {
          identifier.append(input.charAt(i));
        } else if (isFound && END_IDX == END_MARKERS.length) {
          Token token = new Token();
          token.setIdentifier(identifier.toString());
          token.setStartIndex(foundAtIndex);
          token.setEndIndex(i);
          tokens.add(token);
          identifier = new StringBuilder();
          START_IDX = 0;
          END_IDX = 0;
          isFound = false;
        }
      }

      StringBuilder builder = new StringBuilder();
      if (!tokens.isEmpty()) {
        builder.append(input, 0, tokens.get(0).getStartIndex());
      }
      for (int i = 0; i < tokens.size(); i++) {
        try {
          String value;
          Token token = tokens.get(i);
          Token nextToken = i < tokens.size() - 1 ? tokens.get(i + 1) : null;
          int startIndex = builder.length();
          startIndex = Math.min(startIndex, output.length());
          String substring = output.substring(startIndex);
          if (nextToken != null) {
            int endIndex = Math.min(nextToken.getStartIndex(), input.length());
            String separator =
                input.substring(Math.min(token.getEndIndex() + 1, input.length()), endIndex);
            value = substring.split(separator)[0];
            builder.append(separator);
            builder.append(value);
          } else {
            value = substring;
            builder.append(value);
          }
          if (value.contains("\"")) {
            value = value.replaceAll("\"", "\\\\" + '\u0022');
          }
          token.setValue(value);
        } catch (Exception ignored) {

        }
      }

      tokens.stream()
          .filter(token -> token.getValue() != null)
          .collect(Collectors.toList())
          .forEach(token -> {
            result.put(token.getIdentifier(), token.getValue());
          });
    }

    return result;
  }

  static class Token {
    private String identifier;
    private String value;
    private int startIndex;
    private int endIndex;

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }

    public String getIdentifier() {
      return identifier;
    }

    public void setIdentifier(String identifier) {
      this.identifier = identifier;
    }

    public int getStartIndex() {
      return startIndex;
    }

    public void setStartIndex(int startIndex) {
      this.startIndex = startIndex;
    }

    public int getEndIndex() {
      return endIndex;
    }

    public void setEndIndex(int endIndex) {
      this.endIndex = endIndex;
    }
  }
}
