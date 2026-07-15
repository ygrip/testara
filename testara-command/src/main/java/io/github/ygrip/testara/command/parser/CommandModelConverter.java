package io.github.ygrip.testara.command.parser;

import io.github.ygrip.testara.command.CommandExecutor;
import io.github.ygrip.testara.command.ast.CommandCallNode;
import io.github.ygrip.testara.command.ast.CommandNode;
import io.github.ygrip.testara.command.ast.LiteralNode;
import io.github.ygrip.testara.command.ast.RawLiteralNode;
import io.github.ygrip.testara.command.model.CommandModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a {@link CommandCallNode} AST into the existing {@link CommandModel} shape,
 * preserving the parameter nesting that the execution layer expects.
 */
public final class CommandModelConverter {

    /**
     * Convert a top-level {@link CommandCallNode} to a {@link CommandModel}.
     *
     * @param node   the parsed command call.
     * @param parent the parent command name, or {@code null} for top-level calls.
     * @return a fully-populated {@link CommandModel} with cacheable flag set.
     */
    public CommandModel convert(CommandCallNode node, String parent) {
        List<Object> params = new ArrayList<>(node.arguments().size());
        for (CommandNode arg : node.arguments()) {
            params.add(convertArg(arg, node.name()));
        }
        CommandModel model = new CommandModel();
        model.setCommand(node.name());
        model.setParameters(params);
        model.setParentCommand(parent);
        model.setCacheable(CommandExecutor.isCacheableCommand(model));
        return model;
    }

    private Object convertArg(CommandNode node, String parent) {
        return switch (node) {
            case LiteralNode l -> l.value().toString();
            case RawLiteralNode r -> r.value().toString();
            case CommandCallNode c -> convert(c, parent);
        };
    }
}
