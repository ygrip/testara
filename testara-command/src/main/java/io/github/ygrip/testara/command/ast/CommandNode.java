package io.github.ygrip.testara.command.ast;

/**
 * Sealed base for parsed command expression nodes.
 */
public sealed interface CommandNode permits CommandCallNode, LiteralNode, RawLiteralNode {
}
