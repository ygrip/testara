@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="color-scheme" content="light">
  <title>${view.metadata().title()} · Interactive report</title>
  @template.browser.styles()
  @template.browser.theme()
  @template.browser.small()
  @template.browser.explorer()
  @template.browser.spa()
</head>
<body>
  <main class="report-page">
    @template.browser.navigation()
    @template.browser.pageOverview(view)
    @template.browser.pageAdditionalInfo(view)
    @template.browser.pageFailures(view)
    @template.browser.pageHotspots(view)
    @template.browser.pageCoverage(view)
    @template.browser.pageScenarios(view)
    @template.browser.footer(view)
  </main>
  @template.browser.stepHooksDialog(view)
  @template.browser.mediaViewer()
  @template.browser.controller()
  @template.browser.explorerState()
  @template.browser.hotspotsController()
  @template.browser.dialogsController()
  @template.browser.router()
</body>
</html>
