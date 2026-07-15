package io.github.ygrip.testara.command.parser;

import io.github.ygrip.testara.command.ast.CommandCallNode;
import io.github.ygrip.testara.command.ast.CommandNode;
import io.github.ygrip.testara.command.ast.LiteralNode;
import io.github.ygrip.testara.command.ast.RawLiteralNode;
import io.github.ygrip.testara.command.ast.TextSlice;

import java.util.ArrayList;
import java.util.List;

/**
 * One-pass streaming AST parser for Testara command expressions.
 *
 * <p>Grammar (simplified):
 * <pre>
 *   expression  := command_call
 *   command_call := name '(' args ')'
 *   name         := '' (combine) | '!' (raw literal) | identifier
 *   args         := (arg (SEP arg)*)?
 *   arg          := command_call | raw_escape | literal
 *   raw_escape   := '!' '(' raw_content ')'
 *   literal      := any text that does not parse as a command_call
 * </pre>
 *
 * <p>Quick-reject: input that does not end with {@code )} is not a command and causes a
 * {@link CommandParseException} immediately without scanning the rest of the string.
 */
public final class StreamingCommandParser {

    private final CommandParserOptions options;

    public StreamingCommandParser(CommandParserOptions options) {
        if (options == null) throw new NullPointerException("options");
        this.options = options;
    }

    /**
     * Parse {@code input} into a {@link CommandCallNode}.
     *
     * @throws CommandParseException if the input is not a valid command expression.
     */
    public CommandCallNode parse(String input) throws CommandParseException {
        if (input == null) throw new CommandParseException("Input is null");
        String trimmed = input.strip();
        if (!trimmed.endsWith(")")) {
            throw new CommandParseException("Input does not end with ')': " + abbreviate(trimmed));
        }
        if (!trimmed.contains("(")) {
            throw new CommandParseException("Input has no '(': " + abbreviate(trimmed));
        }
        return parseExpression(trimmed, 0);
    }

    // -------------------------------------------------------------------------
    // Internal parsing
    // -------------------------------------------------------------------------

    private CommandCallNode parseExpression(String input, int depth) throws CommandParseException {
        if (depth > options.maxDepth()) {
            throw new CommandParseException.Guardrail("Max parser depth " + options.maxDepth() + " exceeded");
        }

        int openParen = input.indexOf('(');
        if (openParen == -1) {
            throw new CommandParseException("No '(' found in: " + abbreviate(input));
        }

        String name = input.substring(0, openParen).strip();

        if (name.length() > options.maxCommandNameLength()) {
            throw new CommandParseException.Guardrail(
                "Command name exceeds max length " + options.maxCommandNameLength() + ": " + abbreviate(name));
        }

        // Ignored command: everything between parens is raw content, no separator splitting.
        if ("!".equals(name)) {
            int close = findMatchingClose(input, openParen);
            if (close == -1) {
                throw new CommandParseException("No matching ')' for '!' at depth " + depth);
            }
            String rawContent = input.substring(openParen + 1, close);
            return new CommandCallNode("!", List.of(
                new RawLiteralNode(new TextSlice(rawContent, 0, rawContent.length()))));
        }

        int closeParen = findMatchingClose(input, openParen);
        if (closeParen == -1) {
            throw new CommandParseException("No matching ')' for '(' at " + openParen + " in: " + abbreviate(input));
        }
        if (closeParen != input.length() - 1) {
            throw new CommandParseException("Trailing content after closing ')' in: " + abbreviate(input));
        }

        String argsStr = input.substring(openParen + 1, closeParen);
        List<CommandNode> args = parseArgs(argsStr, depth + 1, name);

        return new CommandCallNode(name, args);
    }

    private List<CommandNode> parseArgs(String input, int depth, String parent) throws CommandParseException {
        if (input.isEmpty()) return List.of();

        List<CommandNode> args = new ArrayList<>();
        int start = 0;
        int d = 0;
        char sep = options.separator().charAt(0);
        int len = input.length();

        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);

            // !-at-start: mirrors legacy hasIgnoredParameter path — reverse-scan to find where
            // this raw-literal segment ends, regardless of whether inner parens are balanced.
            if (c == '!' && i == start) {
                int end = reverseMatchingClose(input, i);
                if (end >= i) {
                    args.add(parseArg(input.substring(i, end + 1), depth, parent));
                    if (args.size() >= options.maxArguments()) {
                        throw new CommandParseException.Guardrail(
                            "Argument count exceeds max " + options.maxArguments() + " in command: " + abbreviate(parent));
                    }
                    start = end + 1;
                    if (start < len && input.charAt(start) == sep) {
                        start++;
                    }
                    i = start - 1;
                    continue;
                }
            }

            if (c == '(') {
                d++;
            } else if (c == ')') {
                d--;
            } else if (c == sep && d == 0) {
                args.add(parseArg(input.substring(start, i), depth, parent));
                if (args.size() >= options.maxArguments()) {
                    throw new CommandParseException.Guardrail(
                        "Argument count exceeds max " + options.maxArguments() + " in command: " + abbreviate(parent));
                }
                start = i + 1;
            }
        }

        if (start < len) {
            args.add(parseArg(input.substring(start), depth, parent));
        }
        return args;
    }

    private CommandNode parseArg(String input, int depth, String parent) throws CommandParseException {
        String trimmed = input.strip();

        if (trimmed.isEmpty()) {
            return new LiteralNode(new TextSlice(input, 0, input.length()));
        }

        // Raw literal escape: !( content ) — take everything between !( and the final ) as raw
        // content without balanced-paren matching. Mirrors legacy !-command behaviour and handles
        // inputs like !((.,-,5) where inner parens may be unbalanced.
        if (trimmed.startsWith("!") && trimmed.length() > 2
                && trimmed.charAt(1) == '(' && trimmed.charAt(trimmed.length() - 1) == ')') {
            String rawContent = trimmed.substring(2, trimmed.length() - 1);
            return new CommandCallNode("!", List.of(
                new RawLiteralNode(new TextSlice(rawContent, 0, rawContent.length()))));
        }

        // Nested command: contains '(' and ends with ')'.
        if (trimmed.contains("(") && trimmed.endsWith(")")) {
            try {
                return parseExpression(trimmed, depth);
            } catch (CommandParseException.Guardrail e) {
                throw e;
            } catch (CommandParseException ignored) {
                // not a valid command expression — fall through to literal
            }
        }

        return new LiteralNode(new TextSlice(input, 0, input.length()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Find the closing ')' that balanced-matches the '(' at {@code openIndex}.
     * When '!' is encountered during the scan the legacy heuristic kicks in: return
     * {@code lastIndexOf(')')} immediately. For balanced inputs this always equals the correct
     * matching close; for intentionally unbalanced inputs (e.g. {@code (!((.,-,5),yunaz)}) it
     * produces the legacy-compatible result.
     */
    private static int findMatchingClose(String input, int openIndex) {
        int depth = 0;
        int lastClose = input.lastIndexOf(')');
        for (int i = openIndex; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '!') {
                return lastClose;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * Reverse scan to find where an {@code !}-prefixed parameter ends.
     * Returns the index of the ')' immediately before the first separator found scanning backward,
     * or the position of the last ')' in the string when no such separator exists.
     * Mirrors {@code LegacyCommandParser.reverseFindMatchingClose}.
     */
    private int reverseMatchingClose(String input, int openIndex) {
        char sep = options.separator().charAt(0);
        int lastClose = input.lastIndexOf(')');
        if (lastClose < 0) return -1;
        if (!input.contains(options.separator())) return lastClose;

        for (int i = input.length() - 1; i > openIndex; i--) {
            char c = input.charAt(i);
            if (c == sep && i > 0 && input.charAt(i - 1) == ')') {
                lastClose = i - 1;
                break;
            }
        }
        return lastClose;
    }

    private static String abbreviate(String s) {
        if (s == null) return "null";
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }
}
