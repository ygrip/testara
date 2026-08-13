@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${view.metadata().title()}</title>
</head>
<body style="margin:0;padding:0;background:${theme.pageBackground()};font-family:Arial,Helvetica,sans-serif;color:${theme.textColor()};">
<!--[if mso]><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center"><![endif]-->
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="width:100%;background:${theme.pageBackground()};">
  <tr><td align="center" style="padding:24px 12px;">
    <table role="presentation" width="640" cellpadding="0" cellspacing="0" border="0" style="width:100%;max-width:640px;background:${theme.cardBackground()};border:1px solid ${theme.borderColor()};border-radius:14px;overflow:hidden;">
      @template.component.header(view, theme)
      @template.component.summary(view, theme)
      @template.component.customFields(view, theme)
      @template.component.coverage(view, theme)
      @template.component.failures(view, theme)
      @template.component.durations(view, theme)
      @template.component.footer(view, theme)
    </table>
  </td></tr>
</table>
<!--[if mso]></td></tr></table><![endif]-->
</body>
</html>
