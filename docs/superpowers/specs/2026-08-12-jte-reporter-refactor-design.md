# JTE Reporter Refactor Design

Date: 2026-08-12
Target release: Testara 2.2.0
Scope: `testara-reporter` in PR #1

## 1. Goal

Refactor Testara reporting into a single, typed, component-based JTE rendering architecture and remove Thymeleaf completely.

The migration must:

- replace all Thymeleaf runtime and build dependencies with JTE;
- migrate all existing report styles to JTE;
- add a new modern, clean, email-friendly report style;
- make report rendering component based instead of maintaining large monolithic HTML templates;
- introduce a renderer-independent typed report view model instead of loose `Map<String, Object>` template context values;
- support optional white-label branding consistently across every report style;
- precompile/generate JTE templates at build time and bundle them in the reporter JAR;
- render directly to the output destination instead of building a large intermediate HTML string;
- keep Cucumber parsing/aggregation separate from presentation so rendering can scale without repeating report calculations;
- retain email compatibility, including Outlook conditional comments and VML used by existing templates.

This is a renderer migration, not a dual-engine compatibility layer. There will be no Thymeleaf fallback path after this change.

## 2. Non-goals

This change does not:

- add a second template engine;
- preserve arbitrary user-provided Thymeleaf templates;
- add an email sender or MIME attachment subsystem;
- add PDF generation;
- redesign Cucumber JSON parsing itself unless a small extraction is required to separate aggregation from rendering;
- introduce a general-purpose UI/component framework into the reporter.

## 3. Architecture

The reporting pipeline becomes:

```text
Cucumber JSON
    |
    v
CucumberReportReader / existing parser
    |
    v
CucumberReportAggregator
    |
    v
ReportViewFactory
    |
    v
ReportView                       immutable, renderer independent
    |
    v
ReportRenderer                   rendering boundary
    |
    v
JteReportRenderer                only renderer implementation
    |
    +---- ReportStyle
    |       MODERN
    |       CLASSIC
    |       SIMPLE
    |       SINGLE_PAGE
    |
    v
FileOutput / WriterOutput
```

The important boundary is `ReportView`. Cucumber-specific objects must not be exposed directly to JTE components. That prevents the presentation layer from becoming coupled to Cucumber parsing models and makes future output formats possible without recomputing test data.

### Responsibilities

`CucumberReportAggregator`

- accepts parsed `Feature` data;
- calculates status counts, percentages, coverage, failures, unstable features and duration rankings;
- contains no HTML/template logic.

`ReportViewFactory`

- converts aggregated reporter data into immutable view records;
- resolves branding and report metadata;
- prepares presentation-ready values such as labels, formatted duration ranges and status semantics;
- contains no JTE APIs.

`ReportRenderer`

```java
public interface ReportRenderer {
  void render(ReportStyle style, ReportView report, Path output) throws IOException;
}
```

It is intentionally small. The interface is an architectural boundary, not a legacy extension registry.

`JteReportRenderer`

- owns one reusable precompiled `TemplateEngine`;
- maps `ReportStyle` to a root JTE template;
- renders directly to a file/writer output;
- does not parse templates at report-generation time;
- contains no report aggregation logic.

`CucumberSummaryReportGenerator`

- remains the fluent public orchestration API;
- delegates aggregation/model creation/rendering instead of owning all three concerns;
- no longer creates a template engine per report.

## 4. Typed report view model

Create renderer-independent records under:

```text
io.github.ygrip.testara.reporter.view
```

The root model:

```java
public record ReportView(
    ReportMetadata metadata,
    ReportBranding branding,
    ReportStatusSummary status,
    List<ReportFieldGroup> customFields,
    List<ReportCoverageGroup> coverage,
    List<ReportFailure> frequentFailures,
    List<ReportUnstableFeature> unstableFeatures,
    List<ReportDurationItem> longestScenarios,
    List<ReportDurationItem> longestSteps
) {}
```

Supporting records should be narrow and immutable. Template-visible data must use methods/fields on these types rather than map keys.

Suggested types:

- `ReportMetadata`: title, start/end time, generated time, report link, total feature/scenario count;
- `ReportBranding`: organization name, resolved logo URI, organization detail, white-label state;
- `ReportStatusSummary`: overall status, counts and percentages by status;
- `ReportFieldGroup` / `ReportField`: custom report metadata;
- `ReportCoverageGroup` / `ReportCoverageItem`: suite/feature coverage rows;
- `ReportFailure`: title, error detail, occurrence count, severity/status;
- `ReportUnstableFeature`: feature name and failure percentage;
- `ReportDurationItem`: label, count when applicable, duration range and relative percentage.

JTE root templates receive exactly one `ReportView` parameter. Components receive only the smallest typed object they need.

This removes string-keyed template contracts such as `report`, `results`, `coverage` and `unstableFeatures` from the Java renderer.

## 5. Report styles

Introduce:

```java
public enum ReportStyle {
  MODERN,
  CLASSIC,
  SIMPLE,
  SINGLE_PAGE
}
```

`MODERN` becomes the default for Testara 2.2.0.

The three current template identifiers remain accepted as lightweight aliases so existing Java/CLI configuration does not need a simultaneous rename:

```text
testara-style-report       -> CLASSIC
testara-simple-report      -> SIMPLE
testara-single-page-report -> SINGLE_PAGE
testara-modern-report      -> MODERN
modern                     -> MODERN
classic                    -> CLASSIC
simple                     -> SIMPLE
single-page                -> SINGLE_PAGE
```

This alias mapping does not preserve Thymeleaf. All aliases resolve to JTE styles.

Add the preferred API:

```java
withReportStyle(ReportStyle style)
```

Keep `withReportTemplate(String)` as a deprecated source-compatible adapter that only resolves known style identifiers. It must fail fast for unknown identifiers with an error listing supported styles. Arbitrary external Thymeleaf template loading is removed.

CLI `--single-page-template` remains accepted for compatibility but is documented as a report-style selector and resolves through the same `ReportStyle` parser. A future major release may rename the option without carrying a second implementation path now.

## 6. JTE build/runtime strategy

Use one pinned `${jte.version}` property for both:

- `gg.jte:jte`;
- `gg.jte:jte-maven-plugin`.

The initial implementation should use JTE `3.2.3`, which is verified as published in Maven Central. Dependency and plugin versions must always stay aligned.

Templates live in:

```text
testara-reporter/src/main/jte/
```

Use the Maven plugin `generate` goal during `generate-sources` with `ContentType.Html`. Generated template Java sources are compiled with Testara and bundled into the normal `testara-reporter` JAR. Runtime uses:

```java
TemplateEngine.createPrecompiled(ContentType.Html)
```

The engine is created once and safely shared by reporter instances.

Critical email setting:

```xml
<htmlCommentsPreserved>true</htmlCommentsPreserved>
```

The existing report templates contain Outlook conditional comments and VML blocks. Removing comments during JTE compilation would break those clients. The migration must preserve and test these blocks.

Use normal HTML output escaping for all user/configuration-derived values. Raw output is allowed only for static, repository-owned email compatibility markup where JTE parsing requires it. Branding detail, report names, error messages and custom fields must never use raw/unsafe output.

Binary static-content rendering is not enabled initially. The generated-source/precompiled path already removes runtime parsing and keeps the JAR self-contained without adding another resource-copy pipeline.

## 7. Component structure

Use shared components instead of duplicating each entire report.

```text
src/main/jte/
  report/
    modern.jte
    classic.jte
    simple.jte
    single-page.jte

  layout/
    email.jte

  component/
    branding.jte
    header.jte
    report-meta.jte
    overall-status.jte
    metric-grid.jte
    metric-card.jte
    status-distribution.jte
    custom-fields.jte
    coverage-section.jte
    coverage-row.jte
    failure-section.jte
    failure-card.jte
    unstable-features.jte
    duration-section.jte
    duration-row.jte
    report-link.jte
    footer.jte
    spacer.jte
```

Root report templates are composition files. They decide which components are present and their ordering. They should not contain large duplicated sections.

Where visual differences are only styling, use a typed `ReportTheme`/style token object passed to components instead of copying component markup. A separate component is justified only when structure or semantics differ materially.

The existing three styles are migrated for semantic/visual continuity, not byte-for-byte HTML identity.

## 8. Modern report design

The modern report remains intentionally conservative at the email transport layer:

- table-based layout;
- critical styles inline;
- maximum content width around 640 px;
- no JavaScript;
- no external CSS;
- no icon font;
- no layout dependency on CSS grid/flexbox;
- no SVG dependency for status icons;
- Outlook conditional/VML markup retained where required.

Visual composition:

1. compact brand header with optional logo;
2. report name and execution window;
3. prominent overall status block;
4. status metric cards;
5. compact distribution bar;
6. custom metadata fields when present;
7. coverage summary;
8. failure overview only when failures exist;
9. slowest scenarios and steps;
10. optional full-report call-to-action;
11. branded footer.

Status icons use small text/glyph-based marks with textual labels as the accessibility/fallback source. The report must remain understandable if icons or images are blocked by the email client.

## 9. White-label branding

Expand `ReportConfiguration` using a nested branding configuration consistent with Testara's existing nested property binding pattern.

```properties
testara.report.style=modern

testara.report.branding.organization-name=Blibli
testara.report.branding.logo=classpath:branding/blibli.png
testara.report.branding.detail=Commerce Platform Quality Engineering
```

Java shape:

```java
@Data
@LoadProperties(prefix = "testara.report")
public class ReportConfiguration {
  private Map<String, Object> customFields;
  private String style = "modern";
  private BrandingConfiguration branding = new BrandingConfiguration();
}
```

`BrandingConfiguration` contains organization name, logo and detail.

Resolved defaults:

```text
organization name   Testara
logo                absent
organization detail absent
```

The report/project title remains independent from organization branding. Setting organization name replaces visible framework branding, not `reportName`.

Every style consumes the same `ReportBranding` object and shared header/footer branding components.

### Logo resolution

Introduce `BrandingLogoResolver` with deterministic URI handling:

- `classpath:` -> read resource and embed as a data URI;
- `file:` -> read file and embed as a data URI;
- plain local path -> read file and embed as a data URI;
- `data:image/...` -> pass through after validation;
- `https://` -> pass through unchanged;
- `cid:` -> pass through unchanged for callers that separately construct a MIME message.

Only common email/browser image media types are accepted: PNG, JPEG/JPG, GIF and WEBP. SVG is not accepted for embedded email branding because client support is inconsistent.

A missing/unreadable/unsupported logo logs a warning and renders the report without a logo. Test execution/report generation must not fail because branding artwork is unavailable.

Local logo size is bounded before embedding to prevent accidentally turning a small report into a multi-megabyte HTML file. The implementation limit is 1 MiB of source image data; larger local images are ignored with a warning. Testara will not perform image resizing inside the reporter.

## 10. Email compatibility

All styles share these rules:

- preserve Outlook conditional comments;
- preserve required VML fallback blocks from existing templates;
- use tables for structural layout;
- inline critical styles;
- include image dimensions and meaningful `alt` text;
- make status information textual as well as visual;
- avoid relying on remote fonts;
- avoid JavaScript;
- avoid client-sensitive SVG icons;
- tolerate blocked external images;
- keep a plain readable hierarchy when advanced styling is stripped.

Generated output tests must assert the presence of the Outlook conditional markers after JTE rendering so a future build-plugin change cannot silently strip them.

## 11. Performance and scalability

The current implementation creates a new Thymeleaf engine for each report, disables cache, renders to a large `String`, writes the string, then clears the cache. The refactor removes that lifecycle entirely.

The JTE path must:

- compile/generate templates during the Maven build;
- use one reusable precompiled `TemplateEngine`;
- render directly through `FileOutput` or `WriterOutput`;
- aggregate the Cucumber data once per report;
- build one immutable `ReportView`;
- avoid re-reading report JSON for each template section;
- avoid duplicating calculation logic inside templates;
- keep templates presentation-only;
- prefer immutable records/lists at the renderer boundary.

No speculative caching of report data is added. The main scalable gain comes from eliminating runtime template compilation, large intermediate output strings and repeated calculation/render coupling.

## 12. Error handling

- Unknown report style: fail fast before reading/rendering the report, list valid styles.
- JTE template compilation error: fail the Maven build.
- Runtime render error: propagate as report-generation failure with style and destination in the exception message.
- Invalid branding text: safely HTML escaped.
- Logo read/validation failure: warning, continue without logo.
- Empty report sections: component omitted rather than rendering broken/empty tables.
- Empty feature/scenario collections: calculations must avoid index/division assumptions and render an explicit zero-test state.

The current duration/failure calculations that assume non-empty lists should be hardened as part of the extraction because componentized rendering must handle empty reports safely.

## 13. Migration sequence

The implementation should land in coherent steps while keeping the final PR on one renderer:

1. add typed view models and tests;
2. extract aggregation/view creation from `CucumberSummaryReportGenerator`;
3. add `ReportStyle` parsing and configuration tests;
4. add branding configuration and logo resolver tests;
5. add JTE dependency/plugin and a minimal precompiled renderer contract test;
6. build shared email layout/components;
7. implement `MODERN`;
8. migrate `CLASSIC`;
9. migrate `SIMPLE`;
10. migrate `SINGLE_PAGE`;
11. migrate CLI/style selection to `ReportStyle`;
12. remove all Thymeleaf Java code, dependencies and `.html` templates;
13. verify no `org.thymeleaf`, Thymeleaf template attributes, or legacy template resources remain;
14. run reporter tests and the available Testara reactor verification.

There is no intermediate production state with both renderer engines after the final commit series.

## 14. Testing strategy

Existing reporter tests currently exercise summary generation but mostly assert that generation completes. Preserve those integration fixtures and add targeted assertions.

### Unit tests

`ReportStyleTest`

- all canonical names parse;
- all legacy identifiers map to the intended JTE style;
- unknown values fail with useful message;
- modern is the configured default.

`ReportBrandingFactoryTest`

- Testara defaults;
- organization-name override;
- organization detail;
- null/blank handling.

`BrandingLogoResolverTest`

- classpath embedding;
- local file embedding;
- data URI passthrough;
- HTTPS passthrough;
- CID passthrough;
- unsupported media type warning/fallback;
- missing file warning/fallback;
- source size limit.

`CucumberReportViewFactoryTest`

- status counts/percentages;
- zero tests;
- coverage grouping;
- failure ranking;
- duration ranking;
- custom fields;
- metadata timestamps/link.

### Template/render tests

For every `ReportStyle`:

- render a passed fixture;
- render a failed fixture;
- render an empty/zero-test view;
- output contains the report title and expected status summary;
- default branding contains Testara;
- white-label branding replaces visible Testara brand text;
- organization detail appears in footer;
- optional logo appears only when resolved;
- output contains no unresolved JTE directives;
- output contains no Thymeleaf `data-th-*` attributes;
- Outlook conditional comments remain present where the style uses them;
- HTML does not depend on scripts/external CSS/icon fonts.

Use structural/semantic assertions rather than full 100 KB golden snapshots. Small snapshots are acceptable for isolated components where they improve confidence without making every spacing change a test rewrite.

### Build tests

Maven build must compile JTE templates. A bad template/model reference must fail during compilation, not first fail when a report is generated.

A dependency scan/test should ensure `org.thymeleaf:*` and `thymeleaf-extras-java8time` are absent from the final reporter dependency graph.

## 15. Removal criteria

The migration is complete only when all of the following are true:

- `testara-reporter` has no Thymeleaf dependency;
- root Testara dependency management has no Thymeleaf version/property solely used by reporter;
- no Java class imports Thymeleaf;
- no reporter resource contains `data-th-*`/Thymeleaf expressions;
- the three existing styles and modern style render through JTE;
- JTE templates are built into the reporter JAR;
- `CucumberSummaryReportGenerator` does not construct a template engine or loose template context;
- report rendering writes directly to its output;
- white-labeling works across all styles;
- reporter tests pass independently;
- the full Testara reactor is rerun once the separate unpublished `mitmproxy-grid-java-client:0.2.0` dependency is available.

## 16. Design rationale

The long-term value is not merely replacing one template syntax with another. The durable change is the boundary between report data and rendering.

After this refactor:

- Cucumber processing can evolve without touching email markup;
- report markup can evolve without understanding Cucumber parser internals;
- template/model drift becomes a build-time problem through JTE's typed Java templates;
- styles share components instead of copying large HTML sections;
- branding is a first-class model instead of hardcoded text;
- rendering has predictable build-time compilation and low runtime setup cost;
- a future renderer/output format can consume `ReportView` without duplicating aggregation, while Testara 2.2.0 itself carries only the JTE HTML renderer.
