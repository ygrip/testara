# Testara Reporter Plugin

A Maven plugin that generates HTML test reports from **Cucumber JSON** output. It can clean, merge, aggregate, and render rich single-page HTML reports with charts, coverage breakdowns, failure analysis, and performance metrics.

## Quick Start

Add the plugin to your project's `build` section:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.github.ygrip</groupId>
      <artifactId>testara-reporter-plugin</artifactId>
      <version>${testara.version}</version>
      <executions>
        <execution>
          <id>generate-report</id>
          <phase>post-integration-test</phase>
          <goals>
            <goal>cucumber-summary</goal>
          </goals>
          <configuration>
            <targetLocation>${project.build.directory}/destination/</targetLocation>
            <outputLocation>${project.build.directory}/destination/</outputLocation>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

After running your Cucumber tests, execute:

```bash
mvn post-integration-test
```

The HTML report is written to `target/destination/summary.html` by default.

## Goals

| Goal | Phase | Description |
|------|-------|-------------|
| `testara-reporter:cucumber-summary` | `post-integration-test` | Generate an HTML summary report from Cucumber JSON |
| `testara-reporter:merge-cucumber` | `post-integration-test` | Merge multiple Cucumber JSON files into one |
| `testara-reporter:clean-cucumber` | `post-integration-test` | Normalize scenario outline IDs in Cucumber JSON |
| `testara-reporter:aggregate-summary` | `post-integration-test` | Produce an aggregate summary JSON with counts and durations |

### Full Pipeline

For a complete report pipeline (clean → merge → HTML → aggregate), configure all goals:

```xml
<executions>
  <execution>
    <id>clean-reports</id>
    <phase>post-integration-test</phase>
    <goals><goal>clean-cucumber</goal></goals>
    <configuration>
      <targetLocation>${project.build.directory}/cucumber/</targetLocation>
    </configuration>
  </execution>
  <execution>
    <id>merge-reports</id>
    <phase>post-integration-test</phase>
    <goals><goal>merge-cucumber</goal></goals>
    <configuration>
      <targetLocation>${project.build.directory}/cucumber/</targetLocation>
      <reportName>merged-cucumber.json</reportName>
    </configuration>
  </execution>
  <execution>
    <id>generate-html</id>
    <phase>post-integration-test</phase>
    <goals><goal>cucumber-summary</goal></goals>
    <configuration>
      <targetLocation>${project.build.directory}/cucumber/merged-cucumber.json</targetLocation>
      <outputLocation>${project.build.directory}/reports/</outputLocation>
    </configuration>
  </execution>
  <execution>
    <id>aggregate</id>
    <phase>post-integration-test</phase>
    <goals><goal>aggregate-summary</goal></goals>
    <configuration>
      <targetLocation>${project.build.directory}/cucumber/</targetLocation>
    </configuration>
  </execution>
</executions>
```

## Configuration Reference

### `cucumber-summary`

| Parameter | Property | Default | Description |
|-----------|----------|---------|-------------|
| `targetLocation` | `target-location` | `target/destination/` | Path to Cucumber JSON file or directory containing JSON files |
| `outputLocation` | `output-location` | `target/destination/` | Output directory for the generated HTML report |
| `reportTemplate` | `report-template` | `testara-style-report` | Template name (see [Templates](#templates)) |
| `reportName` | `report-name` | `summary` | Output file name (without `.html` extension) |
| `skip` | — | `false` | Skip this goal execution |

### `merge-cucumber`

| Parameter | Property | Default | Description |
|-----------|----------|---------|-------------|
| `targetLocation` | `target-location` | `target/destination` | Path to directory containing Cucumber JSON files |
| `reportName` | `report-name` | `cucumber.json` | Merged output file name |
| `skip` | — | `false` | Skip this goal execution |

### `clean-cucumber`

| Parameter | Property | Default | Description |
|-----------|----------|---------|-------------|
| `targetLocation` | `target-location` | `target/destination/` | Path to Cucumber JSON file or directory |
| `overwrite` | `overwrite` | `true` | `true` = overwrite source files; `false` = write to `custom-report/` subdirectory |
| `skip` | — | `false` | Skip this goal execution |

### `aggregate-summary`

| Parameter | Property | Default | Description |
|-----------|----------|---------|-------------|
| `targetLocation` | `target-location` | `target/destination` | Path to Cucumber JSON file or directory |
| `reportName` | `report-name` | `aggregate-summary.json` | Output JSON file name |
| `skip` | — | `false` | Skip this goal execution |

### System Properties

These can be set via `-D` flags or in a properties file:

| Property | Description |
|----------|-------------|
| `custom.report.target-location` | Override target location |
| `custom.report.output-location` | Override output location |
| `custom.report.report-template` | Override template name |
| `custom.report.report-name` | Override report name |
| `custom.report.link` | URL shown in the report footer as a link to the full report |

## Templates

Three built-in HTML templates are available:

| Template Name | Description |
|---------------|-------------|
| `testara-style-report` | **Default.** Full-featured layout with charts, coverage, failures, performance — email-friendly |
| `testara-single-page-report` | Compact single-page layout with status badges, coverage, and failure highlights |
| `testara-simple-report` | Minimal layout with summary, statistics, and status badges |

Select a template via the `reportTemplate` configuration parameter:

```xml
<configuration>
  <reportTemplate>testara-single-page-report</reportTemplate>
</configuration>
```

## Customizing the Report

### Custom Fields

Add custom fields (e.g. environment, build number) displayed in the report header via the `testara.report.customFields` property in your properties file:

```properties
testara.report.customFields.Environment=Staging
testara.report.customFields.Build=1234
testara.report.customFields.OS=Linux
testara.report.customFields.User=ci-bot
testara.report.customFields.Branch=main
```

Custom fields appear in a grid (up to 3 columns x 5 rows) at the top of the report. The `link` key is reserved for the report footer URL.

### Report Link

Add a clickable link in the report footer (useful for CI/CD linking back to the full report):

```bash
mvn post-integration-test -Dcustom.report.link=https://ci.example.com/reports/123
```

### Custom Templates

To use your own Thymeleaf template:

1. Create an HTML file using Thymeleaf syntax (`data-th-*` attributes)
2. Place it in `src/main/resources/template/` on the classpath
3. Reference it by name (without `.html`):

```xml
<configuration>
  <reportTemplate>my-custom-template</reportTemplate>
</configuration>
```

Available Thymeleaf context variables:

| Variable | Type | Description |
|----------|------|-------------|
| `report` | Map | Title, overall status, counts, percentages, dates, chart URL |
| `results` | Map | Total scenarios/steps, fastest/slowest features, execution times |
| `customFields` | List&lt;Map&gt; | Custom key-value fields from configuration |
| `coverage` | List&lt;Coverage&gt; | Feature coverage breakdown by suite/tag |
| `frequentFailures` | List&lt;FrequentFailure&gt; | Top 10 most frequent failures |
| `unstableFeatures` | List&lt;FailedFeature&gt; | Features with failures |
| `testFailuresPresent` | boolean | Whether any failures exist |
| `longestSteps` | List&lt;LongestStep&gt; | Top 10 slowest steps |
| `longestScenarios` | List&lt;LongestScenario&gt; | Top 10 slowest scenarios |

### Charts

The report generates a doughnut chart using [QuickChart.io](https://quickchart.io) showing passed/failed/skipped/pending/undefined distributions. The chart URL is embedded in the HTML and works in email clients.

## Standalone CLI

The reporter can also run standalone (outside Maven):

```bash
java -jar testara-reporter-plugin.jar \
  --project-name "My Project" \
  --input-location target/cucumber/ \
  --type cucumber-summary
```

Available CLI modes: `cucumber-summary`, `merge-cucumber`, `clean-cucumber`, `aggregate-summary`, `full-report` (runs all steps).

## Report Content

The generated HTML report includes:

- **Summary** — overall pass/fail status, total scenarios and steps, execution duration
- **Chart** — doughnut chart with pass/fail/skip/pending/undefined breakdown
- **Coverage** — feature-level coverage grouped by suite or tag
- **Frequent Failures** — top 10 most common failure messages
- **Unstable Features** — features with the most failures
- **Longest Scenarios** — top 10 slowest scenarios by duration
- **Longest Steps** — top 10 slowest individual steps
- **Custom Fields** — environment metadata from your configuration

## Example

Minimal configuration for a typical Cucumber + JUnit 5 project:

```xml
<plugin>
  <groupId>io.github.ygrip</groupId>
  <artifactId>testara-reporter-plugin</artifactId>
  <version>${testara.version}</version>
  <executions>
    <execution>
      <phase>post-integration-test</phase>
      <goals>
        <goal>cucumber-summary</goal>
      </goals>
      <configuration>
        <targetLocation>${project.build.directory}/cucumber-reports/</targetLocation>
        <outputLocation>${project.build.directory}/site/</outputLocation>
        <reportTemplate>testara-style-report</reportTemplate>
        <reportName>test-report</reportName>
      </configuration>
    </execution>
  </executions>
</plugin>
```

After `mvn verify`, open `target/site/test-report.html` in a browser.
