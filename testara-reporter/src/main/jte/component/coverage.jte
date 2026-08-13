@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
@if(!view.coverage().isEmpty())
<tr><td style="padding:16px 28px 8px 28px;">
  <div style="font-size:15px;line-height:22px;font-weight:700;margin-bottom:10px;">Coverage</div>
  @for(var group : view.coverage())
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-bottom:12px;border:1px solid ${theme.borderColor()};border-radius:8px;">
      <tr><td colspan="3" style="padding:9px 10px;background:${theme.pageBackground()};font-size:12px;font-weight:700;">${group.title()} <span style="font-weight:400;color:${theme.mutedColor()};">· ${group.duration()}</span></td></tr>
      @for(var item : group.items())
        <tr>
          <td style="padding:8px 10px;border-top:1px solid ${theme.borderColor()};font-size:12px;">${item.name()}</td>
          <td width="90" align="right" style="padding:8px 10px;border-top:1px solid ${theme.borderColor()};font-size:12px;color:${theme.mutedColor()};">${item.testCount()} tests</td>
          <td width="90" align="right" style="padding:8px 10px;border-top:1px solid ${theme.borderColor()};font-size:12px;font-weight:700;color:${theme.accent()};">${item.successRate()}</td>
        </tr>
      @endfor
    </table>
  @endfor
</td></tr>
@endif
