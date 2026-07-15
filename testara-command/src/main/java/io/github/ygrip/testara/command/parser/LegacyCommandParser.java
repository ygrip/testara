package io.github.ygrip.testara.command.parser;

import io.github.ygrip.testara.command.CommandExecutor;
import io.github.ygrip.testara.command.error.InvalidCommandFormatException;
import io.github.ygrip.testara.command.model.CommandModel;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The original ad-hoc streaming parser for Testara command expressions.
 * Used when {@code command.executor.parser-mode=LEGACY} (the default).
 *
 * <p>Preserved verbatim from {@code CommandExecutor} to keep the legacy path stable while
 * the AST parser matures. This class owns all paren-matching heuristics, including the
 * {@code !}-identifier shortcut used in unbalanced-paren inputs.
 */
public final class LegacyCommandParser {

    private final String separator;

    public LegacyCommandParser(String separator) {
        this.separator = separator;
    }

    /**
     * Parse {@code input} into a {@link CommandModel}.
     *
     * @throws InvalidCommandFormatException if the input does not match command syntax.
     */
    public CommandModel parse(String input, String parent) throws InvalidCommandFormatException {
        if (!StringUtils.trim(input).endsWith(")")) {
            throw new InvalidCommandFormatException("Invalid command format: " + input);
        }

        ParseResult parseResult = parseStream(input);
        if (parseResult == null || parseResult.command() == null) {
            throw new InvalidCommandFormatException("Invalid command format: " + input);
        }

        List<Object> parametersList = reconstructParameters(parseResult.command(), parseResult.parameters());

        CommandModel result = new CommandModel();
        result.setCommand(parseResult.command());
        result.setParameters(parametersList);
        result.setParentCommand(parent);
        result.setCacheable(CommandExecutor.isCacheableCommand(result));
        return result;
    }

    // -------------------------------------------------------------------------
    // Stream parsing
    // -------------------------------------------------------------------------

    private ParseResult parseStream(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        int length = input.length();
        int openParenIndex = -1;

        for (int i = 0; i < length; i++) {
            if (input.charAt(i) == '(') {
                openParenIndex = i;
                break;
            }
        }

        if (openParenIndex == -1) {
            return null;
        }

        String command = input.substring(0, openParenIndex);
        if (command.trim().equals(CommandExecutor.IGNORED_COMMAND)) {
            return new ParseResult(command, input.substring(openParenIndex + 1, length - 1));
        }

        int closeParenIndex = findMatchingClose(input, openParenIndex);
        if (closeParenIndex == -1) {
            return null;
        }

        String parameters = closeParenIndex > openParenIndex + 1
            ? input.substring(openParenIndex + 1, closeParenIndex)
            : "";

        return new ParseResult(command, parameters);
    }

    private int findMatchingClose(String input, int openIndex) {
        int length = input.length();
        int depth = 0;
        boolean hasIgnoredIdentifier = false;
        int lastIndexOfClosing = input.lastIndexOf(')');

        for (int i = openIndex; i < length; i++) {
            char c = input.charAt(i);

            if (hasIgnoredIdentifier) {
                return lastIndexOfClosing;
            }

            if (c == '!') {
                hasIgnoredIdentifier = true;
            }

            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    private int reverseFindMatchingClose(String input, int openIndex) {
        int length = input.length();
        char sep = separator.charAt(0);
        int lastIndex = input.lastIndexOf(')');
        if (!input.contains(separator)) {
            return lastIndex;
        }

        for (int i = length - 1; i > openIndex; i--) {
            char c = input.charAt(i);
            if (c == sep && i > 0 && input.charAt(i - 1) == ')') {
                lastIndex = i - 1;
                break;
            }
        }
        return lastIndex;
    }

    // -------------------------------------------------------------------------
    // Parameter reconstruction
    // -------------------------------------------------------------------------

    private List<Object> reconstructParameters(String command, String input) {
        if (input == null || input.trim().isEmpty()) {
            return Collections.emptyList();
        }

        if (command.trim().equals(CommandExecutor.IGNORED_COMMAND)) {
            return Collections.singletonList(input);
        }

        if (!input.contains(separator)) {
            return Collections.singletonList(parseSingleParameter(input, command));
        }

        return parseMultipleParameters(input, command);
    }

    private Object parseSingleParameter(String input, String parentCommand) {
        if (input.contains("(") && input.trim().endsWith(")")) {
            try {
                return parse(input, parentCommand);
            } catch (Exception ignored) {
                return input;
            }
        }
        return input;
    }

    private List<Object> parseMultipleParameters(String input, String parentCommand) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder currentParam = new StringBuilder();

        int length = input.length();
        int depth = 0;
        boolean hasIgnoredParameter = false;
        int ignoredAt = 0;
        char separatorChar = separator.charAt(0);

        for (int i = 0; i < length; i++) {
            char c = input.charAt(i);

            if (hasIgnoredParameter) {
                int lastClosing = reverseFindMatchingClose(input, ignoredAt);
                if (lastClosing > 0) {
                    parameters.add(parseParameterValue(input.substring(ignoredAt, lastClosing + 1), parentCommand));
                    currentParam.setLength(0);
                    depth = 0;
                    i = lastClosing;
                } else {
                    currentParam.append(c);
                }
                ignoredAt = 0;
                hasIgnoredParameter = false;
                continue;
            }

            if (c == '!') {
                String temp = currentParam.toString().trim();
                if (temp.isEmpty()) {
                    ignoredAt = i;
                    hasIgnoredParameter = true;
                }
                currentParam.append(c);
                continue;
            }

            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }

            if (c == separatorChar && depth == 0) {
                parameters.add(parseParameterValue(currentParam.toString(), parentCommand));
                currentParam.setLength(0);
            } else {
                currentParam.append(c);
            }
        }

        parameters.add(parseParameterValue(currentParam.toString(), parentCommand));
        return parameters;
    }

    private Object parseParameterValue(String param, String parentCommand) {
        if (param == null || param.trim().isEmpty()) {
            return param;
        }

        if (param.contains("(") && param.trim().endsWith(")")) {
            try {
                return parse(param, parentCommand);
            } catch (Exception ignored) {
                // treat as regular parameter
            }
        }

        return param;
    }

    // -------------------------------------------------------------------------

    private record ParseResult(String command, String parameters) {}
}
