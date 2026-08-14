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

Scenario-detail selection is local state inside `#scenarios`; opening a scenario does not create a second URL route. Navigating away from `#scenarios` closes scenario detail, and returning to `#scenarios` shows the scenario list.

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

Use `tablist`, `tab`, and `tabpanel` semantics. Left/Right arrow keys move between the two tabs. Tab state is local to this page and does not require a separate hash route.

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

Use a chevron icon instead of textual Expand/Collapse labels. The chevron rotates with state. The disclosure summary keeps a visible `Scenario hooks` label and an accessible expanded/collapsed state.

### Step hooks

Do not render full Before Step and After Step hook cards inline under every scenario step.

Inline step rendering retains attachments produced by step hooks so screenshots or other media remain associated with the owning step.

Scenario detail provides a single **Step hooks** info control near the scenario header. Activating it opens a modal/dialog containing all Before Step and After Step hooks for the scenario, grouped by scenario step.

Each hook entry contains, when available:

- step name / number
- Before Step or After Step type
- status
- duration
- hook location
- output
- error

Do not show argument offsets.

Hook attachments are rendered in the Step Hooks dialog and are also rendered with the owning step in the main scenario detail. This duplication is intentional: the main view preserves attachment ownership, while the dialog preserves complete hook inspection.

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
- direct step attachments
- Before Step / After Step hook attachments

Before/After Step hook metadata, output, location, status, and errors are not expanded inline.

## Error disclosure

Errors use one reusable collapsible component for scenario, step, and hook errors.

Requirements:

- native `<details>` / `<summary>` semantics
- bounded scrollable stack-trace body
- chevron icon instead of textual `Expand` / `Collapse`
- chevron rotates based on open state
- visible error label remains in the summary
- accessible expanded/collapsed state comes from native disclosure semantics plus an accessible control label when required
- long stack traces wrap or scroll within the error container without widening the page

## Attachment viewer

Attachments remain self-contained in the generated report using embedded data URIs when available.

Attachment cards/thumbnails are clickable.

### Images

Clicking an image opens an image-gallery dialog.

Capabilities:

- current image name
- previous / next navigation among images available in the current scenario, including direct step, step-hook, and scenario-hook images
- zoom in
- zoom out
- reset zoom
- fit to view
- keyboard Left / Right navigation
- Escape closes the dialog
- backdrop click closes the dialog
- focus returns to the clicked attachment after close

The image must never force document-level horizontal overflow.

### Video

Clicking a video attachment opens a media-player dialog.

Use native `<video controls>` for playback and browser-native features. Provide a fullscreen action using the Fullscreen API when supported.

The player must fit inside the viewport and preserve the attachment name. Escape closes the dialog when the browser is not currently handling fullscreen exit.

### Audio

Audio remains an inline native `<audio controls>` player. It does not open the media dialog.

### Other files

Unknown/non-media attachments remain downloadable links.

## Browser architecture

Keep the generated report dependency-free and split browser responsibilities into focused JTE components/scripts rather than growing one controller indefinitely.

Browser-side responsibilities:

- `router`: hash route resolution, active page, navigation state, Back/Forward synchronization, closing local scenario detail when leaving Scenarios
- `scenario explorer`: search, filters, sorting, scenario list/detail transition
- `hotspot tabs`: local Slowest Scenarios / Slowest Steps selection and keyboard tab behavior
- `hook dialog`: Step Hooks modal open/close, focus trap, Escape handling, and focus restoration
- `media viewer`: image gallery, zoom controls, image navigation, video dialog, fullscreen, focus trap, and focus restoration
- `disclosures`: native HTML/CSS unless a small script is required only for icon/accessibility synchronization

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

Exact filenames may follow existing conventions, but the boundaries must remain equivalent.

`single-page.jte` becomes composition-only: styles, persistent navigation, report pages, dialogs, footer, and browser controllers.

The footer is global and remains outside route-specific content.

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

The browser may derive scenario-local image order from rendered attachment metadata; this is UI state, not Cucumber-domain data.

## Accessibility and keyboard behavior

- persistent navigation is keyboard reachable
- active route uses `aria-current="page"`
- hotspot tabs use `tablist`, `tab`, and `tabpanel` semantics
- dialogs use `role="dialog"` / `aria-modal="true"`, trap focus while open, and restore focus to the trigger when closed
- Escape closes Step Hooks and media dialogs
- Scenario table rows remain keyboard openable
- reduced-motion preference avoids unnecessary transitions
- icon-only controls have accessible names and tooltips where useful
- native disclosure elements retain keyboard behavior

## Responsive behavior

The SPA navigation may horizontally scroll on very narrow viewports rather than wrapping into a large multi-row header.

Each active page fits within the report content width.

Tables use local horizontal scrolling where required. Metadata, coverage, errors, media, DataTables, and dialogs must never cause page-level horizontal overflow.

Dialogs use a viewport-bounded layout with internal scrolling for tall content.

## Testing

Add focused renderer/browser-contract tests for:

- all six routes rendered
- unknown/missing route resolves to Overview
- navigation contains correct route hashes
- only one report page is active after router initialization
- leaving `#scenarios` closes local scenario detail
- Execution Hotspots contains two tab surfaces and defaults to Slowest Scenarios
- hotspot tabs expose accessible tab semantics
- Additional Information has a bounded scroll container
- Scenario hooks render inside one collapsible container
- inline scenario steps do not render full Before Step / After Step hook cards
- Step Hooks dialog contains all scenario step hooks grouped by step
- offsets are absent from rendered scenario detail and hook dialog
- step-hook attachments remain visible with the owning step
- hook attachments also appear in the Step Hooks dialog
- error component renders icon-based native disclosure and scrollable body
- image attachments expose gallery-open metadata
- image gallery contains previous/next/zoom/reset/fit controls
- video attachments expose player-open metadata
- video dialog contains native controls and fullscreen action
- media/hook dialogs expose modal/focus-restoration hooks
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
- no scenario-detail hash route
- no pagination unless scenario volume proves the current table strategy unusable

## Acceptance criteria

The implementation is complete when:

1. The interactive report opens at Overview and users can navigate between six scoped pages without long-page scrolling.
2. Browser refresh/back/forward preserve page navigation through hashes.
3. Execution Hotspots switches cleanly between Slowest Scenarios and Slowest Steps using accessible tab behavior.
4. Additional Information is bounded and scrollable.
5. Scenario hooks are consolidated into one collapsible container.
6. Before Step / After Step details are removed from inline steps and available through one scenario-level Step Hooks dialog.
7. Step-hook attachments remain visible on the owning step and are inspectable from the Step Hooks dialog.
8. Argument offsets are no longer rendered.
9. Error disclosures use a chevron and bounded scrollable content.
10. Images open in a zoomable scenario-local gallery and videos open in a viewport-safe media player with fullscreen support.
11. Existing Cucumber rich-content propagation remains intact.
12. The generated report remains one offline-capable self-contained HTML file.
13. Focus, Escape, Back/Forward, tab keyboard behavior, and mobile overflow behavior are covered by regression tests.
14. Full project CI passes on the final PR head.
