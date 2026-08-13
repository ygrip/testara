# Testara Reporter

Testara 2.2.0 renders HTML reports with build-time generated JTE templates. The default style is `modern` and all report entry points, including `testara-junit5`, use the same reporter configuration.

## Reporter configuration

Put reporter settings in the same Testara properties source you already load, for example `configuration.properties`:

```properties
# Optional. Defaults to modern.
testara.report.style=modern

# Optional white-label branding.
testara.report.organization-name=Blibli Affiliate
testara.report.organization-logo=https://www.static-src.com/siva/asset/07_2026/White-Blibli-Logo-2026-07-23.png
testara.report.organization-detail=Blibli Affiliate - Automation reporting
```

Supported styles:

- `modern` (default)
- `classic`
- `simple`
- `single-page`

The legacy identifiers `testara-style-report`, `testara-simple-report`, and `testara-single-page-report` are accepted only as style aliases. Thymeleaf templates and arbitrary external Thymeleaf template loading are not supported.

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

`testara-junit5` does not select a report template. When Testara generates the summary report at the end of a Cucumber engine run, reporter style and branding are resolved from `testara.report.*`. This keeps JUnit, the reporter CLI, Maven plugin, and direct Java usage on the same rendering contract.

## Java API

Prefer typed style selection for an explicit per-report override:

```java
CucumberSummaryReportGenerator.fromLocation("/target/destination/")
    .withReportStyle(ReportStyle.MODERN)
    .withReportName("Automation Testing")
    .generateReport();
```

When `withReportStyle(...)` is not called, `testara.report.style` is used. If neither supplies a value, the report is modern.
