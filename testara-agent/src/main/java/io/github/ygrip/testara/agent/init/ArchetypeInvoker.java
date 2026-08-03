package io.github.ygrip.testara.agent.init;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Runs mvn archetype:generate to scaffold a Testara project from a local or remote archetype.
 */
public class ArchetypeInvoker {

  private static final Logger LOG = Logger.getLogger(ArchetypeInvoker.class.getName());
  private static final String ARCHETYPE_GROUP = "io.github.ygrip";
  private static final int TIMEOUT_SECONDS = 180;

  public record ArchetypeRequest(
      String groupId,
      String artifactId,
      String version,
      String packageName,
      String flavor,
      String uiEngine,
      String testaraVersion,
      String javaVersion,
      Path outputDir
  ) {}

  public record ArchetypeResult(
      boolean success,
      String archetypeArtifactId,
      Path generatedDir,
      List<String> errors
  ) {}

  public ArchetypeResult invoke(ArchetypeRequest req) {
    String archetypeArtifactId = resolveArchetypeArtifactId(req.flavor());
    Path targetDir = req.outputDir().resolve(req.artifactId());

    List<String> cmd = buildCommand(req, archetypeArtifactId);

    LOG.info("Invoking archetype: " + String.join(" ", cmd));

    List<String> output = new ArrayList<>();
    int exitCode;
    try {
      ProcessBuilder pb = new ProcessBuilder(cmd)
          .directory(req.outputDir().toFile())
          .redirectErrorStream(true);
      Process proc = pb.start();
      try (BufferedReader br = new BufferedReader(
          new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) output.add(line);
      }
      boolean finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!finished) {
        proc.destroyForcibly();
        return new ArchetypeResult(false, archetypeArtifactId, null,
            List.of("Archetype generation timed out after " + TIMEOUT_SECONDS + "s"));
      }
      exitCode = proc.exitValue();
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      return new ArchetypeResult(false, archetypeArtifactId, null, List.of(e.getMessage()));
    }

    if (exitCode != 0) {
      List<String> errors = output.stream()
          .filter(l -> l.contains("[ERROR]") || l.contains("ERROR"))
          .filter(l -> !l.contains("Downloading") && !l.contains("BUILD"))
          .map(l -> l.replaceFirst("^\\[ERROR\\]\\s*", "").trim())
          .filter(l -> !l.isBlank())
          .distinct()
          .limit(5)
          .toList();
      return new ArchetypeResult(false, archetypeArtifactId, null,
          errors.isEmpty() ? List.of("Maven archetype:generate exited with code " + exitCode) : errors);
    }

    Path generated = Files.exists(targetDir) ? targetDir : req.outputDir();
    fixEscapedMavenProperties(generated.resolve("pom.xml"));
    return new ArchetypeResult(true, archetypeArtifactId, generated, List.of());
  }

  private void fixEscapedMavenProperties(Path pom) {
    if (!Files.exists(pom)) return;
    try {
      String content = Files.readString(pom, StandardCharsets.UTF_8);
      if (content.contains("\\${")) {
        Files.writeString(pom, content.replace("\\${", "${"), StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      LOG.fine("Could not fix escaped Maven properties in " + pom + ": " + e.getMessage());
    }
  }

  private List<String> buildCommand(ArchetypeRequest req, String archetypeArtifactId) {
    String mvn = resolveMvn(req.outputDir());
    List<String> cmd = new ArrayList<>(List.of(
        mvn,
        "archetype:generate",
        "-DarchetypeCatalog=local",
        "-DarchetypeGroupId=" + ARCHETYPE_GROUP,
        "-DarchetypeArtifactId=" + archetypeArtifactId,
        "-DarchetypeVersion=" + req.testaraVersion(),
        "-DgroupId=" + req.groupId(),
        "-DartifactId=" + req.artifactId(),
        "-Dversion=1.0.0-SNAPSHOT",
        "-Dpackage=" + req.packageName(),
        "-DtestaraVersion=" + req.testaraVersion(),
        "-DjavaVersion=" + (req.javaVersion() != null ? req.javaVersion() : "21"),
        "-DinteractiveMode=false",
        "-B",
        "--no-transfer-progress"
    ));

    boolean isUi = req.flavor() != null
        && (req.flavor().startsWith("ui") || req.flavor().equals("all") || req.flavor().equals("fullstack"));
    if (isUi) {
      String engine = req.uiEngine() != null ? req.uiEngine().toLowerCase(Locale.ROOT) : "selenium";
      cmd.add("-DuiEngine=" + engine);
    }

    return cmd;
  }

  private String resolveMvn(Path dir) {
    Path mvnw = dir.resolve("mvnw");
    return Files.exists(mvnw) ? mvnw.toAbsolutePath().toString() : "mvn";
  }

  static String resolveArchetypeArtifactId(String flavor) {
    if (flavor == null) return "testara-archetype-api-cucumber";
    return switch (flavor.toLowerCase(Locale.ROOT)) {
      case "ui", "ui-cucumber", "ui-selenium", "ui-playwright", "ui-appium", "ui-vibium" -> "testara-archetype-ui-cucumber";
      case "all", "fullstack", "full" -> "testara-archetype-all-cucumber";
      default -> "testara-archetype-api-cucumber";
    };
  }
}
