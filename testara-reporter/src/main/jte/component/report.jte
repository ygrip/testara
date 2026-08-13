@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="color-scheme" content="light">
  <meta name="supported-color-schemes" content="light">
  <title>${view.metadata().title()}</title>
  <style>
    body,table,td,a{-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%;}
    table,td{mso-table-lspace:0;mso-table-rspace:0;border-collapse:separate;}
    img{-ms-interpolation-mode:bicubic;border:0;outline:none;text-decoration:none;}
    @media only screen and (max-width:700px){
      .report-shell{width:100%!important;max-width:100%!important;}
      .report-pad{padding-left:18px!important;padding-right:18px!important;}
      .metric-cell{display:block!important;width:100%!important;padding:0 0 8px 0!important;}
      .stack-cell{display:block!important;width:100%!important;padding:0 0 10px 0!important;}
    }
  </style>
</head>
<body style="margin:0;padding:0;background:${theme.pageBackground()};font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,Helvetica,sans-serif;font-size:14px;line-height:21px;color:${theme.textColor()};">
<!--[if mso]><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center"><![endif]-->
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="width:100%;background:${theme.pageBackground()};">
  <tr><td align="center" style="padding:28px 12px;">
    <table class="report-shell" role="presentation" width="680" cellpadding="0" cellspacing="0" border="0" style="width:100%;max-width:680px;background:${theme.cardBackground()};border:1px solid ${theme.borderColor()};border-radius:14px;overflow:hidden;box-shadow:0 8px 28px rgba(15,23,42,.06);">
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
