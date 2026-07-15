package io.github.ygrip.testara.command.ast;

import java.util.List;

/**
 * A parsed command call: name + argument list.
 * Empty name represents the combine command.
 * Name "!" represents the ignored/raw-literal command.
 */
public record CommandCallNode(String name, List<CommandNode> arguments) implements CommandNode {

    public CommandCallNode {
        if (name == null) throw new NullPointerException("name");
        arguments = List.copyOf(arguments);
    }
}
