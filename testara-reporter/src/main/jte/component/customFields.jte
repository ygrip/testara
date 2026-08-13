@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
@if(!view.customFields().isEmpty())
<tr><td style="padding:16px 28px 8px 28px;">
  <div style="font-size:15px;line-height:22px;font-weight:700;margin-bottom:10px;">Environment</div>
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid ${theme.borderColor()};border-radius:8px;">
    @for(var field : view.customFields())
      <tr>
        <td width="34%" style="padding:8px 10px;border-bottom:1px solid ${theme.borderColor()};font-size:12px;color:${theme.mutedColor()};">${field.label()}</td>
        <td style="padding:8px 10px;border-bottom:1px solid ${theme.borderColor()};font-size:12px;font-weight:600;">${field.value()}</td>
      </tr>
    @endfor
  </table>
</td></tr>
@endif
