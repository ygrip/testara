package io.github.ygrip.testara.command.parser;

/**
 * Selects which parsing strategy {@code CommandExecutor} uses.
 *
 * <p>Configure via system property or Testara property file:
 * <pre>
 *   command.executor.parser-mode=LEGACY          # default — full heuristic compatibility
 *   command.executor.parser-mode=STREAMING_AST   # new one-pass AST parser
 * </pre>
 *
 * <ul>
 *   <li>{@link #LEGACY} – the original ad-hoc streaming parser. Handles all existing syntax
 *       including unbalanced-paren heuristics for {@code !(…)} inside combine arguments.</li>
 *   <li>{@link #STREAMING_AST} – the new one-pass AST parser with configurable guard-rails
 *       (depth, argument count, command-name length). Replicates the legacy unbalanced-paren heuristic for { !(…)} arguments.
 *       Introduce behind this gate until compatibility coverage is sufficient.</li>
 * </ul>
 *
 * @see io.github.ygrip.testara.command.parser.StreamingCommandParser
 * @see io.github.ygrip.testara.command.parser.LegacyCommandParser
 */
public enum CommandParserMode {
    LEGACY,
    STREAMING_AST
}
