package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.index.TestaraProjectProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Skill: return a compact project catalog so agents avoid rereading source files.
 */
public class TestaraIndexSkill implements AgentSkill<Void, String> {

  private static final Pattern ACTION = Pattern.compile(
      "@Action\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"[^)]*\\)\\s*(?:public|protected|private)?\\s+[\\w<>\\[\\], ?]+\\s+(\\w+)\\s*\\(([^)]*)\\)",
      Pattern.DOTALL);
  private static final Pattern PAGE_ANNOTATION = Pattern.compile("@Page\\s*\\(([^)]*)\\)", Pattern.DOTALL);
  private static final Pattern PAGE_NAME = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"");
  private static final Pattern PAGE_URL = Pattern.compile("url\\s*=\\s*\"([^\"]*)\"");
  private static final Pattern CLASS_NAME = Pattern.compile("(?:public\\s+)?(?:class|interface)\\s+(\\w+)");
  private static final Pattern LOCATOR = Pattern.compile(
      "(?:public|private|protected)?\\s*static\\s+final\\s+Locator\\s+(\\w+)\\s*=\\s*Locator\\.(\\w+)\\(\"([^\"]*)\"\\)");
  private static final Pattern PROPERTY = Pattern.compile("^\\s*([A-Za-z0-9_.-]+)\\s*=.*$");

  @Override
  public String name() {
    return "testara-index";
  }

  @Override
  public String execute(Void input, AgentContext context) {
    TestaraProjectProfile profile = context.profile();
    Path root = context.projectRoot();
    List<ActionRef> actions = scanActions(root);
    List<PageRef> pages = scanPages(root);
    List<String> propertyKeys = propertyKeys(root);

    StringBuilder sb = new StringBuilder();
    sb.append("testara-index:\n");
    sb.append("project-root: ").append(root).append("\n");
    sb.append("build-tool: ").append(profile.buildTool()).append("\n");
    sb.append("java-version: ").append(profile.javaVersion()).append("\n");
    sb.append("maven-modules: ").append(profile.mavenModules().size()).append("\n");
    sb.append("features: ").append(profile.features().size()).append("\n");
    sb.append("scenarios: ").append(profile.totalScenarios()).append("\n");

    sb.append("\nactions:\n");
    if (actions.isEmpty()) {
      sb.append("- none indexed\n");
    } else {
      actions.forEach(a -> sb.append("- action: \"").append(a.action()).append("\"\n")
          .append("  class: ").append(a.className()).append("\n")
          .append("  method: ").append(a.methodName()).append("\n")
          .append("  parameters: ").append(a.parameters()).append("\n")
          .append("  file: ").append(rel(root, a.sourcePath())).append("\n"));
    }

    sb.append("\npages:\n");
    if (pages.isEmpty()) {
      sb.append("- none indexed\n");
    } else {
      pages.forEach(p -> {
        sb.append("- name: ").append(p.pageName()).append("\n")
            .append("  class: ").append(p.className()).append("\n")
            .append("  url: ").append(p.url().isBlank() ? "(configured externally)" : p.url()).append("\n")
            .append("  file: ").append(rel(root, p.sourcePath())).append("\n")
            .append("  locators:\n");
        if (p.locators().isEmpty()) {
          sb.append("  - none indexed\n");
        } else {
          p.locators().forEach(l -> sb.append("  - ").append(l.name())
              .append(": Locator.").append(l.kind()).append("(\"").append(l.value()).append("\")\n"));
        }
      });
    }

    sb.append("\ntags:\n");
    if (profile.tags().isEmpty()) {
      sb.append("- none indexed\n");
    } else {
      profile.tags().stream()
          .sorted(Comparator.comparing(t -> t.tag().toLowerCase(Locale.ROOT)))
          .forEach(t -> sb.append("- ").append(t.tag())
              .append(" scenarios=").append(t.scenarioCount())
              .append(" features=").append(t.featureCount()).append("\n"));
    }

    sb.append("\nstep-definitions:\n");
    if (profile.stepDefinitions().isEmpty()) {
      sb.append("- none indexed\n");
    } else {
      profile.stepDefinitions().stream().limit(60)
          .forEach(s -> sb.append("- ").append(s.annotation()).append(" \"")
              .append(s.expression()).append("\" -> ").append(s.className()).append("\n"));
    }

    sb.append("\nproperties:\n");
    if (propertyKeys.isEmpty()) {
      sb.append("- none indexed\n");
    } else {
      propertyKeys.forEach(k -> sb.append("- ").append(k).append("\n"));
    }
    sb.append("property-values: redacted\n");
    return sb.toString();
  }

  private List<ActionRef> scanActions(Path root) {
    List<ActionRef> actions = new ArrayList<>();
    for (Path file : javaFiles(root)) {
      try {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        if (!source.contains("@Action")) continue;
        String className = className(source);
        Matcher matcher = ACTION.matcher(source);
        while (matcher.find()) {
          actions.add(new ActionRef(matcher.group(1), className, matcher.group(2),
              parameterNames(matcher.group(3)), file));
        }
      } catch (IOException ignored) {
      }
    }
    actions.sort(Comparator.comparing(ActionRef::action));
    return actions;
  }

  private List<PageRef> scanPages(Path root) {
    List<PageRef> pages = new ArrayList<>();
    for (Path file : javaFiles(root)) {
      try {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        if (!source.contains("@Page")) continue;
        Matcher pageMatcher = PAGE_ANNOTATION.matcher(source);
        if (!pageMatcher.find()) continue;
        String annotation = pageMatcher.group(1);
        String className = className(source);
        pages.add(new PageRef(firstMatch(PAGE_NAME, annotation, className), firstMatch(PAGE_URL, annotation, ""),
            className, locatorRefs(source), file));
      } catch (IOException ignored) {
      }
    }
    pages.sort(Comparator.comparing(PageRef::pageName));
    return pages;
  }

  private List<LocatorRef> locatorRefs(String source) {
    List<LocatorRef> refs = new ArrayList<>();
    Matcher matcher = LOCATOR.matcher(source);
    while (matcher.find()) refs.add(new LocatorRef(matcher.group(1), matcher.group(2), matcher.group(3)));
    return refs;
  }

  private List<String> propertyKeys(Path root) {
    TreeSet<String> keys = new TreeSet<>();
    for (String rel : List.of("src/test/resources/application.properties",
        "src/test/resources/configuration.properties",
        "src/main/resources/application.properties",
        "src/main/resources/configuration.properties",
        "application.properties",
        "configuration.properties")) {
      Path file = root.resolve(rel);
      if (!Files.exists(file)) continue;
      try {
        Files.readAllLines(file, StandardCharsets.UTF_8).forEach(line -> {
          Matcher matcher = PROPERTY.matcher(line);
          if (matcher.find() && !line.strip().startsWith("#")) keys.add(matcher.group(1));
        });
      } catch (IOException ignored) {
      }
    }
    return List.copyOf(keys);
  }

  private List<Path> javaFiles(Path root) {
    List<Path> files = new ArrayList<>();
    try {
      Files.walkFileTree(root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
          return name.equals("target") || name.equals(".git") ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
          if (file.toString().endsWith(".java")) files.add(file);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException ignored) {
    }
    return files;
  }

  private String className(String source) {
    return firstMatch(CLASS_NAME, source, "");
  }

  private String firstMatch(Pattern pattern, String source, String fallback) {
    Matcher matcher = pattern.matcher(source);
    return matcher.find() ? matcher.group(1) : fallback;
  }

  private List<String> parameterNames(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    return Pattern.compile(",").splitAsStream(raw)
        .map(String::strip)
        .filter(s -> !s.isBlank())
        .map(s -> {
          String[] parts = s.split("\\s+");
          return parts.length == 0 ? s : parts[parts.length - 1].replace("...", "");
        })
        .collect(Collectors.toList());
  }

  private String rel(Path root, Path file) {
    try {
      return root.relativize(file).toString();
    } catch (IllegalArgumentException e) {
      return file.toString();
    }
  }

  private record ActionRef(String action, String className, String methodName, List<String> parameters,
      Path sourcePath) {}
  private record PageRef(String pageName, String url, String className, List<LocatorRef> locators,
      Path sourcePath) {}
  private record LocatorRef(String name, String kind, String value) {}
}
