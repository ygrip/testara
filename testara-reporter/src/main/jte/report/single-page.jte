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
</head>
<body>
  <main class="report-page">
    @template.browser.hero(view)
    @template.browser.overview(view)
    @template.browser.insights(view)
    @template.browser.results(view)
    @template.browser.footer(view)
  </main>
  @template.browser.controller()
</body>
</html>
