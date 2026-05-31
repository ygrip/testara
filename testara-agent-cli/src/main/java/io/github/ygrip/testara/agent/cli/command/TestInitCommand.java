package io.github.ygrip.testara.agent.cli.command;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.knowledge.JsonlKnowledgeStore;
import io.github.ygrip.testara.agent.llm.DisabledLlmClient;
import io.github.ygrip.testara.agent.skill.AgentContext;
import io.github.ygrip.testara.agent.skill.TestInitSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Map;

@Command(name = "/test-init", aliases = {"test-init"},
    description = "Bootstrap a new Testara project or integrate into an existing one",
    mixinStandardHelpOptions = true)
public class TestInitCommand implements Runnable {

  @Option(names = "--type", defaultValue = "api",
      description = "Project type: api, ui, database, streaming, fullstack")
  private String type;

  @Option(names = "--base-package", defaultValue = "io.github.ygrip.testara",
      description = "Base Java package for generated classes")
  private String basePackage;

  @Option(names = "--engine", defaultValue = "selenium",
      description = "UI engine (ui type only): selenium, playwright, appium")
  private String engine;

  @Option(names = "--integrate-existing", defaultValue = "false",
      description = "Integrate into existing Maven project instead of bootstrapping")
  private boolean integrateExisting;

  @Option(names = "--project", defaultValue = ".", description = "Target project root")
  private Path projectRoot;

  @Option(names = "--preview", defaultValue = "false",
      description = "Preview files to be created without writing to disk")
  private boolean preview;

  @Override
  public void run() {
    Path root = projectRoot.toAbsolutePath().normalize();
    // Default: write files. Use --preview to only print what would be created.
    Map<String, String> opts = Map.of("write", preview ? "false" : "true");
    AgentContext ctx = new AgentContext(root,
        JsonlKnowledgeStore.loadProfile(root), AgentMode.PATCH,
        new DisabledLlmClient(), opts);
    System.out.println(new TestInitSkill().execute(
        new TestInitSkill.Input(type, basePackage, engine, integrateExisting), ctx));
  }
}
