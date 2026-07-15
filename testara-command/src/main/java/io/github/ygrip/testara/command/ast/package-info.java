/**
 * AST node types for parsed Testara command expressions.
 *
 * <p>The sealed hierarchy:
 * <pre>
 *   {@link io.github.ygrip.testara.command.ast.CommandNode}          (sealed interface)
 *   ├── {@link io.github.ygrip.testara.command.ast.CommandCallNode}   – a parsed command call: name + arg list
 *   ├── {@link io.github.ygrip.testara.command.ast.LiteralNode}       – a plain string argument
 *   └── {@link io.github.ygrip.testara.command.ast.RawLiteralNode}    – content of a !(…) escape
 * </pre>
 *
 * <p>{@link io.github.ygrip.testara.command.ast.TextSlice} is a zero-copy
 * {@link java.lang.CharSequence} view that avoids materialising the substring until
 * {@code toString()} is called, keeping allocation low for large literal arguments.
 */
package io.github.ygrip.testara.command.ast;
