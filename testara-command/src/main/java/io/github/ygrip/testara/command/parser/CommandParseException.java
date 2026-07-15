package io.github.ygrip.testara.command.parser;

/**
 * Thrown by {@link StreamingCommandParser} when input cannot be parsed as a command expression.
 * {@link Guardrail} is a subclass used for hard limit violations (depth, arg-count, name-length)
 * that must not be silently swallowed by the literal fall-through in argument parsing.
 */
public class CommandParseException extends Exception {

    public CommandParseException(String message) {
        super(message);
    }

    /**
     * Hard limit violation — must not be caught and converted to a literal.
     */
    public static final class Guardrail extends CommandParseException {
        public Guardrail(String message) {
            super(message);
        }
    }
}
