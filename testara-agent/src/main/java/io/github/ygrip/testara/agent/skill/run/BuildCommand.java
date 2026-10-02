package io.github.ygrip.testara.agent.skill.run;

import java.util.List;

/**
 * A resolved build-tool invocation.
 *
 * @param argv    executable first, one token per element; passed to {@link ProcessBuilder}, never a shell
 * @param display human-readable form for plans and reports (may quote values; never executed)
 */
public record BuildCommand(List<String> argv, String display) {
  public BuildCommand {
    argv = List.copyOf(argv);
  }
}
