package io.github.ygrip.testara.command.parser;

/**
 * Immutable guard-rail settings for {@link StreamingCommandParser}.
 */
public record CommandParserOptions(int maxDepth, int maxArguments, int maxCommandNameLength, String separator) {

    public CommandParserOptions {
        if (separator == null || separator.isEmpty()) throw new IllegalArgumentException("separator must not be empty");
        if (maxDepth < 1) throw new IllegalArgumentException("maxDepth must be >= 1");
        if (maxArguments < 1) throw new IllegalArgumentException("maxArguments must be >= 1");
        if (maxCommandNameLength < 1) throw new IllegalArgumentException("maxCommandNameLength must be >= 1");
    }

    public static CommandParserOptions defaults() {
        return new CommandParserOptions(20, 50, 64, ",");
    }
}
