package io.github.ygrip.testara.agent.cli.command;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.skill.TestInitSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "/test-init",
  aliases = {"test-init"},
  description = "Bootstrap a new Testara project or integrate into an existing one",
  mixinStandardHelpOptions = true
)
public class TestInitCommand implements Callable<Integer> {

  @Option(names = "--type",
    description = "Project type: api, ui, sql, mongo, kafka, fullstack (combined with --slices; default api)"
  )
  private String type;

  @Option(names = {"--slices", "--capabilities"}, split = ",",
    description = "Comma-separated capabilities: api,ui,sql,mongo,kafka,elastic")
  private List<String> slices;

  @Option(names = "--group-id", description = "Maven group ID (e.g. com.company)")
  private String groupId;

  @Option(names = "--artifact-id", description = "Maven artifact ID (e.g. my-automation)")
  private String artifactId;

  @Option(names = "--base-package", description = "Base Java package (auto-derived from groupId.artifactId if not set)")
  private String basePackage;

  @Option(names = "--engine",
    defaultValue = "selenium",
    description = "UI engine (ui type only): selenium, playwright, appium, vibium"
  )
  private String engine;

  @Option(names = "--integrate-existing",
    defaultValue = "false",
    description = "Integrate into existing Maven project instead of bootstrapping"
  )
  private boolean integrateExisting;

  @Option(names = "--examples",
    defaultValue = "false",
    description = "Also generate demo sample feature/page/request artifacts. Default false."
  )
  private boolean includeExamples;

  @Option(names = "--project", defaultValue = ".", description = "Target project root")
  private Path projectRoot;

  @Option(names = "--preview",
    defaultValue = "false",
    description = "Preview files to be created without writing to disk"
  )
  private boolean preview;

  @Option(names = {"-y", "--yes"},
    defaultValue = "false",
    description = "Skip interactive prompts and use defaults or provided values"
  )
  private boolean nonInteractive;

  @Option(names = "--interactive",
    defaultValue = "false",
    description = "Force interactive prompts even when no console is detected"
  )
  private boolean forceInteractive;

  @Option(names = "--no-compile",
    defaultValue = "false",
    description = "Skip the test-compile gate that runs after files are written"
  )
  private boolean skipCompile;

  @Override
  public Integer call() {
    Path root = CliSupport.root(projectRoot);

    boolean interactive = !nonInteractive && (forceInteractive || System.console() != null);
    if (interactive) {
      prompt(root);
    } else {
      applyDefaults(root);
    }

    Map<String, String> opts = CliSupport.projectOptions(root);
    String blocked = CliSupport.requestWrite("test-init", opts, !preview);
    if (blocked != null) {
      return CliSupport.print(blocked);
    }
    opts.put("includeExamples", Boolean.toString(includeExamples));
    if (skipCompile) {
      opts.put("compile", "false");
    }
    AgentMode mode = AgentMode.APPLY;
    if (preview) {
      mode = AgentMode.PATCH;
    }
    // A null type lets the skill combine --slices without an implied api type.
    return CliSupport.print(new TestInitSkill().execute(
      new TestInitSkill.Input(
        type,
        basePackage,
        engine,
        integrateExisting,
        groupId,
        artifactId,
        slices
      ), CliSupport.context(root, mode, opts)
    ));
  }

  private void applyDefaults(Path root) {
    String dirName = root.getFileName() != null ?
      root.getFileName()
      .toString() :
      "automation";
    if (groupId == null)
      groupId = "io.github.ygrip";
    if (artifactId == null)
      artifactId = toKebab(dirName);
    if (basePackage == null)
      basePackage = groupId + "." + toPackage(artifactId);
  }

  private void prompt(Path root) {
    String dirName = root.getFileName() != null ?
      root.getFileName()
      .toString() :
      "automation";
    String defaultGroupId = groupId != null ? groupId : "io.github.ygrip";
    String defaultArtifactId = artifactId != null ? artifactId : toKebab(dirName);
    String defaultType = type != null ? type : "api";

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
      System.out.println("\n┌─ Testara Init ─────────────────────────────────┐");
      System.out.println("│  Press ENTER to accept the default value [...]  │");
      System.out.println("└────────────────────────────────────────────────┘\n");

      String coordinateMode = askChoice(reader, "Maven coordinates", "auto", new String[] {"auto", "manual"});
      if ("manual".equals(coordinateMode)) {
        groupId = ask(reader, "Group ID", defaultGroupId);
        artifactId = ask(reader, "Artifact ID", defaultArtifactId);
      } else {
        groupId = defaultGroupId;
        artifactId = defaultArtifactId;
        System.out.printf("  Using generated coordinates: %s:%s%n", groupId, artifactId);
      }
      type = askChoice(
        reader,
        "Project type",
        defaultType,
        new String[] {"api", "ui", "sql", "mongo", "kafka", "fullstack"}
      );
      String selected = ask(reader, "Additional capabilities (comma-separated: api,ui,sql,mongo,kafka,elastic; blank keeps type)", "");
      if (!selected.isBlank()) slices = List.of(selected.split(","));

      String defaultPkg = groupId + "." + toPackage(artifactId);
      basePackage = ask(reader, "Base package", defaultPkg);

      if ("ui".equals(type) || "fullstack".equals(type)) {
        engine = askChoice(
          reader,
          "UI engine",
          engine != null ? engine : "selenium",
          new String[] {"selenium", "playwright", "appium"}
        );
      }

      System.out.println();
      System.out.printf("  groupId:    %s%n", groupId);
      System.out.printf("  artifactId: %s%n", artifactId);
      System.out.printf("  type:       %s%n", type);
      if (slices != null && !slices.isEmpty()) System.out.printf("  capabilities: %s%n", String.join(", ", slices));
      System.out.printf("  package:    %s%n", basePackage);
      System.out.println();

    } catch (Exception e) {
      // Fall back to defaults on any I/O issue, but tell the user their answers were not used.
      System.err.println("Interactive prompt failed (" + e.getMessage() + "); using defaults.");
      applyDefaults(root);
    }
  }

  private String ask(BufferedReader reader, String label, String defaultValue) throws Exception {
    System.out.printf("  %s [%s]: ", label, defaultValue);
    System.out.flush();
    String input = reader.readLine();
    return (input == null || input.isBlank()) ? defaultValue : input.trim();
  }

  private String askChoice(BufferedReader reader, String label, String defaultValue, String[] choices)
    throws Exception {
    System.out.printf("  %s (%s) [%s]: ", label, String.join("/", choices), defaultValue);
    System.out.flush();
    String input = reader.readLine();
    if (input == null || input.isBlank())
      return defaultValue;
    String v = input.trim()
      .toLowerCase(Locale.ROOT);
    for (String choice : choices) {
      if (choice.startsWith(v) || v.equals(choice))
        return choice;
    }
    return defaultValue; // invalid input → default
  }

  private String toKebab(String s) {
    return s.toLowerCase(Locale.ROOT)
      .replaceAll("[^a-z0-9]+", "-")
      .replaceAll("^-|-$", "");
  }

  private String toPackage(String s) {
    return s.toLowerCase(Locale.ROOT)
      .replaceAll("[^a-z0-9]+", "");
  }
}
