# Testara Reporter

Testara 2.2.0 renders HTML reports with build-time generated JTE templates. The default `modern` report is designed as a readable, email-safe execution summary. An optional self-contained interactive companion can be generated for browser use.

All report entry points, including `testara-junit5`, use the same reporter configuration.

## Reporter configuration

Put reporter settings in the same Testara properties source you already load, for example `configuration.properties`:

```properties
# Optional. Defaults to modern.
testara.report.style=modern

# Optional browser-first interactive companion. Defaults to false.
testara.report.interactive.enabled=false

# Optional white-label branding.
testara.report.organization-name=Blibli Affiliate
testara.report.organization-logo=https://www.static-src.com/siva/asset/07_2026/White-Blibli-Logo-2026-07-23.png
testara.report.organization-detail=Blibli Affiliate - Automation reporting

# Optional arbitrary report metadata.
testara.report.custom-fields.environment=QA2
testara.report.custom-fields.browser=Chrome 150
testara.report.custom-fields.build=affiliate-2026.08.13.4
testara.report.custom-fields.team=Affiliate Platform
```

Supported styles:

- `modern` (default, email-safe)
- `classic`
- `simple`
- `single-page` (browser-oriented interactive report)

The legacy identifiers `testara-style-report`, `testara-simple-report`, and `testara-single-page-report` are accepted only as style aliases. Thymeleaf templates and arbitrary external Thymeleaf template loading are not supported.

## Modern email report

The modern report uses the visual language of Setara UI while staying conservative enough for email clients:

- system fonts with a 14 px base size and readable line height;
- clear branded header and overall-status treatment;
- pass-rate, scenario, step and execution metrics;
- email-safe status distribution, failure-frequency, feature-coverage and duration bars;
- explicit text labels in addition to color and icons;
- table-based structural layout and Outlook conditional markup;
- no JavaScript, external stylesheets, web fonts or chart service dependency.

## Interactive companion

Enable the browser-first report with:

```properties
testara.report.interactive.enabled=true
```

When enabled while using an email-safe style, Testara produces both the configured summary file and `report.html`. For the normal JUnit 5 summary this is typically:

```text
summary.html  -> email-safe report
report.html   -> self-contained interactive report
```

The interactive report contains its data, CSS and a small dependency-free JavaScript controller in one HTML file. It requires no server or CDN and supports:

- full-text search across scenarios, features, suites, tags, steps and errors;
- status, suite, feature and tag filters;
- failed-only mode;
- sorting by duration, scenario name or status;
- expandable scenario details with individual step status/duration and failure text;
- status distribution, failure-frequency, unstable-feature, coverage and performance visualizations.

Interactive generation is disabled by default because JavaScript is generally stripped or disabled by email clients. The email-safe summary remains the portable delivery artifact.

## Custom fields

Any property under `testara.report.custom-fields.*` becomes report metadata. These values are shown under **Additional information** in both modern email and interactive reports.

For example:

```properties
testara.report.custom-fields.environment=QA2
testara.report.custom-fields.browser=Chrome 150
testara.report.custom-fields.build-number=affiliate-72139
testara.report.custom-fields.git-commit=8ad31ce
testara.report.custom-fields.executed-by=Jenkins
```

Testara also includes the current OS and user in that section. The `link` custom-field key remains reserved by the reporter and is not rendered as a normal metadata field.

## White-label behavior

`organization-name` replaces visible Testara framework branding while the report title remains the test/project name.

`organization-logo` accepts:

- `https://...` remote images;
- `classpath:...` resources;
- `file:...` URIs;
- plain local file paths;
- supported `data:image/...;base64,...` values;
- `cid:...` references when the caller constructs the surrounding email MIME message.

Local/classpath PNG, JPEG, and GIF images up to 1 MiB are embedded as data URIs. An unreadable or unsupported logo is omitted with a warning rather than failing report generation.

`organization-detail` is rendered in the footer.

## JUnit 5

`testara-junit5` does not select a report template. When Testara generates the summary report at the end of a Cucumber engine run, reporter style, interactive generation, custom fields and branding are resolved from `testara.report.*`. This keeps JUnit, the reporter CLI, Maven plugin, and direct Java usage on the same rendering contract.

## Java API

Prefer typed style selection for an explicit per-report override:

```java
CucumberSummaryReportGenerator.fromLocation("/target/destination/")
    .withReportStyle(ReportStyle.MODERN)
    .withReportName("Automation Testing")
    .generateReport();
```

When `withReportStyle(...)` is not called, `testara.report.style` is used. If neither supplies a value, the report is modern.
