# Testara Interactive Report SPA Design

Date: 2026-08-15
Status: Approved design for PR #1

## Goal

Refactor the self-contained interactive Testara report from one long scrolling dashboard into a scoped, offline-friendly SPA-style report while preserving the current typed `ReportView`, JTE rendering, white-label support, and zero-runtime-dependency requirement.

The report must remain a single generated HTML file. No frontend framework, CDN dependency, backend service, or network access is required after generation.

## Navigation model

Use hash routing with one active report page at a time. Supported routes:

- `#overview`
- `#additional-info`
- `#failures`
- `#hotspots`
- `#coverage`
- `#scenarios`

Unknown or missing hashes resolve to `#overview`.

A compact persistent navigation bar remains visible while moving between report pages. Route changes update the hash so refresh, deep links, browser Back, and browser Forward work naturally.

Only the active report page is displayed. Changing route must not rely on long-page scrolling.

## Page composition

### Overview

Contains only the run summary:

- hero / branding
- quality signal
- pass-rate metric
- execution-time metric
- scenario duration range
- quality/status distribution

The Overview page must not include Additional Information, Failure Signals, Hotspots, Coverage, or Scenario Explorer.

### Additional Information

Dedicated page for run metadata and configured custom fields.

The metadata table is placed inside a bounded vertical scroll container so large custom-field sets do not make the page excessively long.

Long labels and values wrap without causing horizontal document overflow.

### Failure Signals

Dedicated page containing:

- frequent failure signatures
- unstable features

Existing ranking behavior is retained.

### Execution Hotspots

Dedicated page with an internal tab control:

- **Slowest scenarios**
- **Slowest steps**

Only one hotspot list is visible at a time. Slowest scenarios is the default tab.

Tab state is local to this page and does not require a separate hash route.

### Functional Coverage

Dedicated page containing the current grouped coverage view.

The coverage data remains bounded and vertically scrollable for large reports.

### Scenarios

Dedicated page containing only the Scenario Explorer and scenario detail experience.

The scenario table remains a real table at all viewport sizes and may scroll horizontally on narrow screens.

Opening a scenario replaces the scenario-list surface within the Scenarios page. Closing the scenario detail restores the table and focus to the previously selected row.

## Scenario detail structure

### Header

Keep:

- scenario status
- Scenario Outline/example label when available
- scenario name
- feature
- suite
- tags
- duration

Do not display Cucumber argument offsets. Step source metadata may show:

- step line
- step definition / match location

### Scenario hooks

Before Scenario and After Scenario hooks are grouped into one collapsible **Scenario hooks** container.

The collapsed summary shows a compact status/count indication. Expanding it shows both groups with hook status, location, duration, output, error, and attachments.

Use an icon-only chevron indicator for expanded/collapsed state. The icon rotates with state and the control has an accessible text label.

### Step hooks

Do not render full Before Step and After Step hook cards inline under every scenario step.

Inline step rendering retains attachments produced by step hooks so screenshots or other media remain associated with the owning step.

Scenario detail provides a single **Step hooks** info control near the scenario header. Activating it opens a modal/dialog containing all Before Step and After Step hooks for the scenario, grouped by scenario step.

Each hook entry may include:

- step name / number
- Before Step or After Step type
- status
- duration
- hook location
- output
- error

Do not show argument offsets.

Hook attachments may also be shown in the dialog when useful, but the same attachment remains visible with its owning step in the main scenario detail.

## Step presentation

Scenario steps remain numbered with a visually separate rounded number badge.

Each step stays left aligned and may render:

- keyword + step name
- duration
- source line
- match location
- error
- Cucumber output
- comments
- DataTable
- DocString
- attachments

Before/After Step hook details are not expanded inline.

## Error disclosure

Errors use one reusable collapsible component for scenario, step, and hook errors.

Requirements:

- native disclosure semantics where practical
- bounded scrollable stack-trace body
- chevron icon instead of textual `Expand` / `Collapse`
- chevron rotates based on open state
- accessible summary label describes whether the error details are expanded
- long stack traces wrap or scroll within the error container without widening the page

## Attachment viewer

Attachments remain self-contained in the generated report using embedded data URIs when available.

Attachment cards/thumbnails are clickable.

### Images

Clicking an image opens an image-gallery dialog.

Capabilities:

- current image name
- previous / next navigation among images available in the current scenario
- zoom in
- zoom out
- reset zoom
- fit to view
- keyboard Left / Right navigation
- Escape closes the dialog
- backdrop click may close the dialog

The image must never force document-level horizontal overflow.

### Video

Clicking a video attachment opens a media-player dialog.

Use native `<video controls>` for playback and browser-native features. Provide a fullscreen action using the Fullscreen API when supported.

The player must fit inside the viewport and preserve the attachment name.

### Audio

Audio may remain an inline/native audio player unless the final shared media viewer can support it without additional complexity.

### Other files

Unknown/non-media attachments remain downloadable links.

## Browser architecture

Keep the generated report dependency-free and split browser responsibilities into focused JTE components/scripts rather than growing one controller indefinitely.

Recommended browser-side responsibilities:

- `router`: hash route resolution, active page, navigation state, Back/Forward synchronization
- `scenario explorer`: search, filters, sorting, scenario list/detail transition
- `hotspot tabs`: local Slowest Scenarios / Slowest Steps selection
- `hook dialog`: Step Hooks modal open/close and focus management
- `media viewer`: image gallery, zoom controls, video dialog, fullscreen
- `disclosures`: primarily native HTML/CSS, JavaScript only when accessibility/state synchronization requires it

The implementation may keep these as JTE script components rather than JavaScript modules if that better matches the existing single-file renderer.

## JTE composition

Refactor the interactive report into scoped page templates. Suggested structure:

```text
testara-reporter/src/main/jte/browser/
├── navigation.jte
├── page-overview.jte
├── page-additional-info.jte
├── page-failures.jte
├── page-hotspots.jte
├── page-coverage.jte
├── page-scenarios.jte
├── scenario-detail.jte
├── scenario-hooks.jte
├── step-hooks-dialog.jte
├── attachments.jte
├── media-viewer.jte
├── error.jte
├── router.jte
└── controller.jte
```

Exact filenames may follow existing conventions, but the boundaries should remain equivalent.

`single-page.jte` becomes composition-only: styles, navigation, report pages, dialogs, footer, and browser controllers.

## Data model boundary

Do not reparse raw Cucumber JSON in JavaScript.

Continue creating a typed immutable `ReportView` in Java. Browser templates consume only the typed view.

Existing hook/attachment propagation remains authoritative:

- direct step attachments
- Before Step / After Step attachments
- Before Scenario / After Scenario attachments
- hook errors and output
- DocString metadata
- DataTables
- match location

Add view helpers only when they reduce template/controller duplication. Do not introduce presentation-only fields into the Cucumber POJOs unless necessary for correct source mapping.

## Accessibility and keyboard behavior

- persistent navigation is keyboard reachable
- active route uses `aria-current="page"`
- tabs use proper tab semantics or equivalent accessible button state
- dialogs trap focus while open and restore focus to the trigger when closed
- Escape closes scenario hook/media dialogs
- Scenario table rows remain keyboard openable
- reduced-motion preference avoids unnecessary transitions
- icon-only controls have accessible names/tooltips

## Responsive behavior

The SPA navigation may horizontally scroll on very narrow viewports rather than wrapping into a large multi-row header.

Each active page fits within the report content width.

Tables use local horizontal scrolling where required. Metadata, coverage, errors, media, DataTables, and dialogs must never cause page-level horizontal overflow.

Dialogs use a viewport-bounded layout with internal scrolling for tall content.

## Testing

Add focused renderer/browser-contract tests for:

- all six routes rendered
- only one report page active at a time after controller initialization contract
- unknown/missing route resolves to Overview
- navigation contains correct route hashes
- Execution Hotspots contains two tab surfaces and defaults to Slowest Scenarios
- Additional Information has bounded scroll container
- Scenario hooks render inside one collapsible container
- inline scenario steps do not render full Before Step / After Step hook cards
- Step Hooks dialog contains all scenario step hooks
- offsets are absent from rendered scenario detail
- hook attachments remain visible with the owning step
- error component renders icon-based disclosure and scrollable body
- image attachments expose gallery-open metadata
- video attachments expose player-open metadata
- media viewer contains zoom/reset/fit/navigation/fullscreen controls
- Scenario Explorer search/filter/sort regression coverage remains green
- existing DataTable, DocString, hook, embedding, image, video, and Scenario Outline fixtures remain green

Full reactor CI remains the final gate because hook/status changes affect aggregate report status as well as browser rendering.

## Non-goals

- no frontend framework
- no external JS/CSS/CDN
- no network-loaded media
- no separate HTML page per report section
- no raw-Cucumber parsing in JavaScript
- no change to email-safe report behavior except shared Java model fixes required for correctness
- no pagination unless scenario volume proves the current table strategy unusable

## Acceptance criteria

The implementation is complete when:

1. The interactive report opens at Overview and users can navigate between six scoped pages without long-page scrolling.
2. Browser refresh/back/forward preserve page navigation through hashes.
3. Execution Hotspots switches cleanly between Slowest Scenarios and Slowest Steps.
4. Additional Information is bounded and scrollable.
5. Scenario hooks are consolidated into one collapsible container.
6. Before Step / After Step details are removed from inline steps and available through one scenario-level Step Hooks dialog.
7. Step-hook attachments remain visible on the owning step.
8. Argument offsets are no longer rendered.
9. Error disclosures use a chevron and bounded scrollable content.
10. Images open in a zoomable gallery and videos open in a viewport-safe media player with fullscreen support.
11. Existing Cucumber rich-content propagation remains intact.
12. The generated report remains one offline-capable self-contained HTML file.
13. Focus, Escape, Back/Forward, and mobile overflow behavior are covered by regression tests.
14. Full project CI passes on the final PR head.
