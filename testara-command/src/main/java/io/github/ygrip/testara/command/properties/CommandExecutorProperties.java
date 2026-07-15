package io.github.ygrip.testara.command.properties;

import io.github.ygrip.testara.command.parser.CommandParserMode;
import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.registry.RegistryScope;
import lombok.Data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>CommandExecutorProperties class.</p>
 *
 * @author yunaz.ramadhan on 1/1/2021
 * @version $Id: $Id
 */
@Data
@TestComponent(scope = RegistryScope.GLOBAL)
@LoadProperties(prefix = "command.executor")
public class CommandExecutorProperties {
  private Integer maxPrintableCharacters = 100000;
  private Integer maxExecutionCacheSize = 500;
  private Integer maxParseCacheSize = 1000;
  private String defaultCommandSeparator = ",";
  private Boolean cacheEnabled = true;
  private Integer scanTimeout = 10;
  private Set<String> scanLocations = new HashSet<>(Collections.singletonList("io.github.ygrip.testara"));
  /** Selects the parsing strategy. {@code LEGACY} (default) preserves all heuristics; {@code STREAMING_AST} enables the new one-pass parser. */
  private CommandParserMode parserMode = CommandParserMode.LEGACY;
  /** Maximum command-call nesting depth enforced by the STREAMING_AST parser. */
  private Integer maxParserDepth = 20;
  /** Maximum argument count per command call enforced by the STREAMING_AST parser. */
  private Integer maxParserArguments = 50;
  /** Maximum command name length (characters) enforced by the STREAMING_AST parser. */
  private Integer maxParserCommandNameLength = 64;
}
