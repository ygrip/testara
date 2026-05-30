package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.index.DriverIndex;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only skill: lists all UI drivers and engines discovered in the project.
 * Agent-friendly output — no LLM or TestFramework init required.
 */
public class ListUiCatalogSkill implements AgentSkill<Void, String> {

  @Override
  public String name() { return "list-ui-catalog"; }

  @Override
  public String execute(Void input, AgentContext context) {
    List<DriverIndex> drivers = context.profile().drivers().stream()
        .sorted(Comparator.comparing(DriverIndex::engineClass).thenComparing(DriverIndex::name))
        .collect(Collectors.toList());

    String format = context.options().getOrDefault("format", "markdown");
    return "json".equals(format) ? renderJson(drivers) : renderMarkdown(drivers);
  }

  private String renderMarkdown(List<DriverIndex> drivers) {
    if (drivers.isEmpty()) {
      return "# Available UI Drivers & Engines\n\nNo drivers found in project source.\n";
    }
    Map<String, List<DriverIndex>> byEngine = drivers.stream()
        .collect(Collectors.groupingBy(DriverIndex::engineClass));

    StringBuilder sb = new StringBuilder();
    sb.append("# Available UI Drivers & Engines\n\n");
    sb.append("Found **").append(byEngine.size()).append("** engine(s) with **")
      .append(drivers.size()).append("** driver(s).\n\n");

    byEngine.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> {
          sb.append("## Engine: `").append(e.getKey()).append("`\n\n");
          sb.append("| Driver | Platforms | Browser | Class |\n");
          sb.append("|--------|-----------|---------|-------|\n");
          e.getValue().stream()
              .sorted(Comparator.comparing(DriverIndex::name))
              .forEach(d -> {
                sb.append("| `").append(d.name()).append("` | ");
                sb.append(d.platforms().isEmpty() ? "DEFAULT" : String.join(", ", d.platforms()));
                sb.append(" | ");
                sb.append(d.browserName().isBlank() ? "—" : d.browserName());
                sb.append(" | `").append(d.className()).append("` |\n");
              });
          sb.append("\n");
        });

    sb.append("## Usage\n\n");
    sb.append("Configure in `application.properties` or `testara.yml`:\n\n");
    sb.append("```properties\n");
    sb.append("testara.ui.engine=selenium        # or playwright, appium\n");
    sb.append("testara.ui.driver=chrome          # must match driver name above\n");
    sb.append("```\n");
    return sb.toString();
  }

  private String renderJson(List<DriverIndex> drivers) {
    Map<String, List<DriverIndex>> byEngine = drivers.stream()
        .collect(Collectors.groupingBy(DriverIndex::engineClass));

    StringBuilder sb = new StringBuilder();
    sb.append("{\n  \"engines\": [\n");
    List<Map.Entry<String, List<DriverIndex>>> engines = byEngine.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .collect(Collectors.toList());

    for (int i = 0; i < engines.size(); i++) {
      Map.Entry<String, List<DriverIndex>> entry = engines.get(i);
      sb.append("    {\n");
      sb.append("      \"engine\": \"").append(entry.getKey()).append("\",\n");
      sb.append("      \"drivers\": [\n");
      List<DriverIndex> engineDrivers = entry.getValue().stream()
          .sorted(Comparator.comparing(DriverIndex::name)).collect(Collectors.toList());
      for (int j = 0; j < engineDrivers.size(); j++) {
        DriverIndex d = engineDrivers.get(j);
        sb.append("        {\n");
        sb.append("          \"name\": \"").append(d.name()).append("\",\n");
        sb.append("          \"platforms\": [").append(jsonStringArray(d.platforms())).append("],\n");
        sb.append("          \"browserName\": \"").append(d.browserName()).append("\",\n");
        sb.append("          \"className\": \"").append(d.className()).append("\"\n");
        sb.append("        }").append(j < engineDrivers.size() - 1 ? "," : "").append("\n");
      }
      sb.append("      ]\n");
      sb.append("    }").append(i < engines.size() - 1 ? "," : "").append("\n");
    }
    sb.append("  ]\n}");
    return sb.toString();
  }

  private String jsonStringArray(List<String> items) {
    return items.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", "));
  }
}
