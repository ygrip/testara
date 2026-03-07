package io.github.ygrip.testara.command.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class CommandInfo {
  private final List<String> subCommand;
  private final List<String> aliases;
  private final String name;
  private final boolean overwrite;
  private final boolean cacheable;

  public CommandInfo(Class<?> commandClass) {
    if (!CommandLogic.class.isAssignableFrom(commandClass)) {
      throw new RuntimeException("Invalid class provided for command info");
    }
    List<String> aliases = new ArrayList<>();
    List<String> subCommand = new ArrayList<>();
    String name = null;
    CommandTag tag = commandClass.getAnnotation(CommandTag.class);
    if (tag != null) {
      String[] commands = tag.subCommands();
      String[] alias = tag.alias();
      name = tag.command().trim().toLowerCase();
      if (commands != null && commands.length > 0) {
        subCommand =
            Arrays.stream(commands).map(item -> item.toLowerCase().trim()).distinct().collect(Collectors.toList());
      }
      if (alias != null && alias.length > 0) {
        aliases = Arrays.stream(alias).map(item -> item.toLowerCase().trim()).distinct().collect(Collectors.toList());
      }
      this.overwrite = tag.overwrite();
      this.cacheable = tag.cacheable();
    } else {
      this.overwrite = false;
      this.cacheable = false;
    }
    this.name = name;
    this.aliases = aliases;
    this.subCommand = subCommand;
  }

  public String name() {
    return this.name;
  }

  public boolean overwrite() {
    return this.overwrite;
  }

  public boolean isCacheable() {
    return cacheable;
  }

  public List<String> aliases() {
    return this.aliases;
  }

  public List<String> subCommands() {
    return this.subCommand;
  }
}
