@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
@if(!view.customFields().isEmpty())
<tr><td class="report-pad" style="padding:16px 30px 8px 30px;">
  <div style="font-size:16px;line-height:23px;font-weight:800;margin-bottom:4px;">Additional information</div>
  <div style="font-size:13px;line-height:19px;color:${theme.mutedColor()};margin-bottom:10px;">Run context and custom metadata supplied by the automation project.</div>
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid ${theme.borderColor()};border-radius:10px;overflow:hidden;">
    @for(var field : view.customFields())
      <tr>
        <td width="34%" style="padding:10px 12px;border-bottom:1px solid ${theme.borderColor()};font-size:13px;line-height:19px;color:${theme.mutedColor()};background:#F8FAFC;font-weight:650;">${field.label()}</td>
        <td style="padding:10px 12px;border-bottom:1px solid ${theme.borderColor()};font-size:13px;line-height:19px;font-weight:650;word-break:break-word;">${field.value()}</td>
      </tr>
    @endfor
  </table>
</td></tr>
@endif
