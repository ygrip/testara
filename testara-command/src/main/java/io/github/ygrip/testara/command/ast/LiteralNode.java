package io.github.ygrip.testara.command.ast;

/**
 * A plain literal value — not a command call.
 */
public record LiteralNode(CharSequence value) implements CommandNode {

    public LiteralNode {
        if (value == null) throw new NullPointerException("value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
