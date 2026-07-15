package io.github.ygrip.testara.command.ast;

/**
 * The raw content inside a {@code !(…)} escape — treated as a literal without further parsing.
 */
public record RawLiteralNode(CharSequence value) implements CommandNode {

    public RawLiteralNode {
        if (value == null) throw new NullPointerException("value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
