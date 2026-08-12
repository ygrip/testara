# JTE Reporter Refactor Design

Date: 2026-08-12  
Target release: Testara 2.2.0  
Scope: `testara-reporter` in PR #1

## 1. Goal

Refactor Testara reporting into one typed, component-based JTE rendering architecture and remove Thymeleaf completely.

The migration must:

- replace all Thymeleaf runtime/build dependencies with JTE;
- migrate every existing report style to JTE;
- add a new modern, clean, email-friendly report style;
- replace monolithic HTML templates with reusable report components;
- replace loose `Map<String, Object>` template contracts with immutable typed view models;
- support optional white-label branding across every style;
- generate JTE template classes at build time and bundle them in the reporter JAR;
- render directly to the output file rather than producing a large intermediate HTML string;
- aggregate report data once, separately from rendering;
- preserve email compatibility, including Outlook conditional comments and VML.

This is a renderer migration, not a dual-engine compatibility layer. The completed change has no Thymeleaf fallback path.

## 2. Non-goals

This change does not:

- retain arbitrary user-provided Thymeleaf templates;
- add another template engine beside JTE;
- add an email sender/MIME subsystem;
- add PDF generation;
- redesign Cucumber JSON parsing beyond extracting aggregation responsibilities;
- introduce a general-purpose web UI/component framework.

## 3. Architecture

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
ReportRenderer                   architectural boundary
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
FileOutput
```

The durable boundary is `ReportView`. Cucumber parser/domain objects must not be passed into JTE templates.

### `CucumberReportAggregator`

- accepts parsed `Feature` data;
- calculates status counts/percentages, coverage, frequent failures, unstable features and duration rankings;
- contains no JTE or HTML logic.

### `ReportViewFactory`

- turns aggregate data into immutable rendering records;
- resolves metadata and branding;
- prepares presentation-ready labels/duration ranges/status semantics;
- contains no JTE APIs.

### `ReportRenderer`

```java
public interface ReportRenderer {
  void render(ReportStyle style, ReportView report, Path output) throws IOException;
}
```

The interface is deliberately small. It defines the data/rendering boundary and is not a plugin registry or legacy extension mechanism.

### `JteReportRenderer`

- owns one reusable precompiled `TemplateEngine`;
- maps each `ReportStyle` to exactly one root JTE template;
- renders directly with JTE `FileOutput`;
- performs no template parsing/compilation at report-generation time;
- performs no report aggregation.

### `CucumberSummaryReportGenerator`

The existing fluent public entry point remains, but becomes orchestration only: read -> aggregate -> build view -> render. It no longer creates a template engine or a loose template context.

## 4. Typed view model

Create immutable records under:

```text
io.github.ygrip.testara.reporter.view
```

Root type:

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

Required view records:

- `ReportMetadata`: report title, start/end time, generated time, report link, total feature count, total scenario count;
- `ReportBranding`: organization name, resolved logo URI, organization detail, white-label flag;
- `ReportStatusSummary`: overall status plus ordered status metrics;
- `ReportStatusMetric`: status, label, count, percentage;
- `ReportFieldGroup` and `ReportField`: custom metadata;
- `ReportCoverageGroup` and `ReportCoverageItem`: suite/feature coverage rows;
- `ReportFailure`: title, error detail, count, severity/status;
- `ReportUnstableFeature`: feature name and failure percentage;
- `ReportDurationItem`: label, optional call count, duration range and relative percentage.

A root JTE template receives exactly one `ReportView`. Components receive only the narrow typed values they need.

## 5. Report styles and compatibility

Introduce:

```java
public enum ReportStyle {
  MODERN,
  CLASSIC,
  SIMPLE,
  SINGLE_PAGE
}
```

`MODERN` is the Testara 2.2.0 default.

Existing style identifiers remain as lightweight aliases only. They do not retain Thymeleaf:

```text
testara-modern-report      -> MODERN
testara-style-report       -> CLASSIC
testara-simple-report      -> SIMPLE
testara-single-page-report -> SINGLE_PAGE
modern                     -> MODERN
classic                    -> CLASSIC
simple                     -> SIMPLE
single-page                -> SINGLE_PAGE
```

Preferred fluent API:

```java
withReportStyle(ReportStyle style)
```

Keep `withReportTemplate(String)` as a deprecated source-compatible adapter that resolves only these known IDs. Unknown IDs fail fast with the accepted styles. Arbitrary external Thymeleaf template loading is removed.

CLI `--single-page-template` remains accepted in 2.2.0 but resolves through the same `ReportStyle` parser. Help text describes it as a style selector. This preserves command compatibility without preserving an old renderer.

## 6. JTE build/runtime strategy

Use one pinned `${jte.version}` for both:

- `gg.jte:jte`;
- `gg.jte:jte-maven-plugin`.

The dependency and Maven plugin must always use the exact same version. The implementation will pin the latest stable release verified in Maven Central at implementation time, never a range, `LATEST`, or snapshot. This avoids freezing the design to a patch version that may be stale before Testara 2.2.0 ships.

Templates live under:

```text
testara-reporter/src/main/jte/
```

Use the JTE Maven plugin `generate` goal in `generate-sources`, with `ContentType.Html`. The generated Java template sources compile with Testara and are bundled in the normal reporter JAR. Runtime creates one engine with:

```java
TemplateEngine.createPrecompiled(ContentType.Html)
```

Critical build setting:

```xml
<htmlCommentsPreserved>true</htmlCommentsPreserved>
```

JTE normally removes HTML comments, while Testara's email markup relies on Outlook conditional comments containing VML. These comments must survive compilation and are covered by renderer tests.

All configuration/report/error text uses JTE's normal HTML escaping. `$unsafe{}`/raw output is forbidden for user/configuration-derived values. Raw sections are permitted only for repository-owned static email-compatibility markup when necessary.

Binary static-content rendering is intentionally not enabled in 2.2.0. Generated/precompiled templates already eliminate runtime parsing and keep a self-contained JAR without another resource-copy pipeline.

## 7. Component structure

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

Root report templates are composition files. They control ordering and inclusion, not detailed repeated markup.

Where styles differ only visually, shared components receive a typed `ReportTheme` containing presentation tokens. New component markup is created only when the structure/semantics actually differ. This prevents four report styles from becoming four copies of the same 100 KB template again.

The existing styles target semantic and recognizable visual continuity, not byte-for-byte HTML equivalence.

## 8. Modern report

The modern style is visually cleaner while remaining deliberately conservative for email clients:

- table-based structural layout;
- critical styles inline;
- approximately 640 px maximum content width;
- no JavaScript;
- no external CSS;
- no remote fonts;
- no icon font;
- no layout dependency on CSS grid/flexbox;
- no SVG status icons;
- existing Outlook conditional/VML techniques retained where required.

Composition:

1. compact organization/framework header with optional logo;
2. report title and execution window;
3. prominent overall status;
4. status metric cards;
5. compact distribution bar;
6. optional custom metadata;
7. coverage summary;
8. failure overview only when failures exist;
9. longest scenarios and steps;
10. optional full-report CTA;
11. branded footer.

Status icons use simple glyphs plus explicit text labels. The report remains understandable when styling/images are stripped or blocked.

## 9. White-label branding

Extend `ReportConfiguration` with nested branding, following Testara's existing nested configuration pattern:

```properties
testara.report.style=modern

testara.report.branding.organization-name=Blibli
testara.report.branding.logo=classpath:branding/blibli.png
testara.report.branding.detail=Commerce Platform Quality Engineering
```

Java configuration shape:

```java
@Data
@LoadProperties(prefix = "testara.report")
public class ReportConfiguration {
  private Map<String, Object> customFields;
  private String style = "modern";
  private BrandingConfiguration branding = new BrandingConfiguration();
}
```

Defaults:

```text
organization name   Testara
logo                absent
organization detail absent
```

Organization branding is independent from `reportName`. A white-label name replaces visible Testara branding but does not replace a report title such as `Checkout Regression`.

Every style consumes the same `ReportBranding` model and shared branding/header/footer components.

### Logo resolution

Introduce `BrandingLogoResolver`:

- `classpath:` -> read and embed as data URI;
- `file:` -> read and embed as data URI;
- plain local path -> read and embed as data URI;
- `data:image/...` -> validate and pass through;
- `https://` -> pass through unchanged;
- `cid:` -> pass through unchanged for callers that separately build MIME messages.

Embedded local/data images accept PNG, JPEG/JPG and GIF. SVG and WEBP are rejected for embedded branding because broad email-client compatibility is the priority.

A missing, unreadable, oversized or unsupported logo logs a warning and renders without a logo. Report generation must not fail because branding artwork is unavailable.

Local/classpath source images are limited to 1 MiB before base64 encoding. Larger assets are ignored with a warning. Testara does not perform image resizing inside the reporter.

Data URI embedding is for self-contained report portability. Where a mail pipeline supports MIME attachments, `cid:` remains the preferred way to reference an attached logo; Testara reporter itself does not construct the MIME message.

## 10. Email compatibility contract

Every style must:

- preserve Outlook conditional comments and required VML fallback blocks;
- use tables for structural layout;
- inline critical styles;
- use explicit image dimensions and meaningful `alt` text;
- encode status semantically in text as well as color/icon;
- avoid remote fonts, JavaScript and external stylesheets;
- avoid SVG/icon-font dependencies;
- tolerate blocked external images;
- remain readable when advanced styling is stripped.

Renderer tests assert that Outlook conditional markers remain in generated output, preventing a future JTE/plugin configuration change from silently stripping them.

## 11. Performance and scalability

The current implementation creates a new Thymeleaf engine for each report, disables template caching, renders the whole result into a `String`, writes it, and clears the cache. That lifecycle is removed.

The JTE path must:

- generate templates during the Maven build;
- use one reusable precompiled `TemplateEngine`;
- render directly via `FileOutput`;
- read/aggregate Cucumber data once per report;
- build one immutable `ReportView`;
- avoid re-reading report files for individual sections;
- keep calculations out of templates;
- avoid duplicated calculations across styles/components;
- make all renderer-bound lists immutable copies.

No speculative cross-report cache is added. The scalability gain comes from build-time template compilation, reusable renderer state, direct output, one aggregation pass and smaller componentized templates.

## 12. Error handling

- unknown style: fail before reading/rendering, with accepted values;
- JTE template/model mismatch: fail Maven compilation;
- runtime render failure: propagate with report style and destination path in the message;
- branding/report/custom/error text: HTML escaped;
- invalid logo: warn and continue without logo;
- empty optional section: omit component;
- zero-feature/zero-scenario report: render a valid explicit zero-test state without division/index errors.

The extraction must harden existing ranking/calculation code that currently assumes non-empty lists.

## 13. Migration sequence

1. add typed view records and tests;
2. extract aggregation/view creation from `CucumberSummaryReportGenerator`;
3. add `ReportStyle` parsing/configuration tests;
4. add branding configuration and logo resolver tests;
5. add aligned JTE dependency/plugin plus a minimal precompiled renderer test;
6. build shared email layout/components;
7. implement `MODERN`;
8. migrate `CLASSIC`;
9. migrate `SIMPLE`;
10. migrate `SINGLE_PAGE`;
11. move Java/CLI style selection to `ReportStyle`;
12. remove all Thymeleaf Java code, dependencies and `.html` templates;
13. remove root Thymeleaf dependency/version management that is no longer used elsewhere;
14. verify no Thymeleaf imports, `data-th-*`, expressions or template resources remain;
15. run reporter verification and the available Testara reactor checks.

The final PR contains only the JTE renderer. There is no completed state in which both template engines are carried.

## 14. Testing strategy

Existing summary-generation fixtures remain useful, but they currently mostly prove that generation completes. Add behavioral assertions rather than relying on giant full-file snapshots.

### Unit tests

`ReportStyleTest`

- canonical names and legacy aliases resolve correctly;
- unknown values fail clearly;
- modern is the default.

`ReportBrandingFactoryTest`

- Testara defaults;
- organization override/detail;
- blank/null normalization.

`BrandingLogoResolverTest`

- classpath and local file embedding;
- data URI validation/passthrough;
- HTTPS/CID passthrough;
- PNG/JPEG/GIF support;
- SVG/WEBP rejection;
- missing/oversized source warning and fallback.

`CucumberReportViewFactoryTest`

- counts/percentages;
- zero tests;
- coverage grouping;
- failure ranking;
- duration ranking;
- custom fields;
- metadata/link values.

### Renderer tests

For each `ReportStyle`:

- render passed, failed and zero-test views;
- assert report title and status summary;
- assert default Testara branding;
- assert white-label name replaces visible framework branding;
- assert organization detail in footer;
- assert logo inclusion/absence appropriately;
- assert no unresolved JTE directives;
- assert no Thymeleaf `data-th-*` output;
- assert Outlook conditional comments survive where required;
- assert no script/external CSS/icon-font dependency.

Small component snapshots are acceptable when useful. Do not use full-report golden snapshots that make harmless spacing changes expensive.

### Build/dependency tests

- Maven must compile JTE templates, so invalid template/model references fail the build;
- reporter dependency verification must confirm `org.thymeleaf:*` and `thymeleaf-extras-java8time` are absent;
- repository search must confirm Thymeleaf remains nowhere after the migration. Current repository search shows its usage is limited to the reporter plus root dependency/version management, so full removal is in scope.

## 15. Completion criteria

The refactor is complete only when:

- reporter and root POMs contain no Thymeleaf dependency/version used by Testara;
- no Java source imports Thymeleaf;
- no reporter resource contains Thymeleaf markup;
- all four styles render through JTE;
- generated JTE classes are included in the reporter JAR;
- `CucumberSummaryReportGenerator` owns no template engine/context construction;
- rendering writes directly to output;
- white-labeling works consistently for all styles;
- zero-test reports render safely;
- reporter tests pass independently;
- the full Testara reactor is rerun once the separate unpublished `mitmproxy-grid-java-client:0.2.0` dependency is available.

## 16. Rationale

The long-term value is the separation between report data and rendering, not merely a syntax swap.

After this refactor:

- Cucumber processing can evolve without editing email markup;
- report design can evolve without depending on Cucumber parser internals;
- template/model drift is detected during the build by typed JTE templates;
- report styles share components instead of copying giant HTML documents;
- branding is first-class typed data rather than hardcoded text;
- runtime rendering has predictable low setup cost;
- future output formats can consume `ReportView` without duplicating aggregation, while Testara 2.2.0 itself carries only one HTML renderer: JTE.
