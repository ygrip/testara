/**
 * Command parsing infrastructure for the Testara command executor.
 *
 * <h2>Parser modes</h2>
 * <p>Two parsers are available, selected via the {@code command.executor.parser-mode} property:
 * <ul>
 *   <li>{@code LEGACY} (default) — the original ad-hoc streaming parser in
 *       {@link io.github.ygrip.testara.command.parser.LegacyCommandParser}.
 *       Handles all existing syntax including unbalanced-paren heuristics.</li>
 *   <li>{@code STREAMING_AST} — the new one-pass AST parser in
 *       {@link io.github.ygrip.testara.command.parser.StreamingCommandParser}.
 *       Suitable for standard command expressions; set via
 *       {@code -Dcommand.executor.parser-mode=STREAMING_AST}.</li>
 * </ul>
 *
 * <h2>Supported grammar (STREAMING_AST)</h2>
 * <pre>
 *   expression  := command_call
 *   command_call := name '(' args ')'
 *   name         := ''          — combine command: (.,-,5) → ".-5"
 *                  | '!'        — ignored/raw-literal: !(.,-,5) → ".,-,5"
 *                  | identifier — e.g. random, request, sizeof
 *   args         := (arg (',' arg)*)?
 *   arg          := command_call | raw_escape | literal
 *   raw_escape   := '!' '(' content ')'   — e.g. !(,) escapes a literal comma
 *   literal      := any text that does not parse as a command_call
 * </pre>
 *
 * <h2>Quick reference — common syntax</h2>
 * <pre>
 *   uuid()                              — no-arg command
 *   random(10,NUMERIC)                  — two-arg command
 *   substring(automation,4)             — string literal args
 *   sizeof(request($['items']))         — nested command
 *   (.,-,5)                             — combine: produces ".-5"
 *   (prefix-,properties(key),)          — combine with command arg
 *   !(,)                                — raw literal comma (escape for split separator)
 *   split(properties(csv),!(,))         — split by literal comma
 *   loop(.,-,5)                         — loop: produces ".-.-.-.-.
 * </pre>
 *
 * <h2>Guard-rail properties (STREAMING_AST only)</h2>
 * <pre>
 *   command.executor.max-parser-depth              (default 20)
 *   command.executor.max-parser-arguments          (default 50)
 *   command.executor.max-parser-command-name-length (default 64)
 * </pre>
 *
 */
package io.github.ygrip.testara.command.parser;
