@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
@if(!view.coverage().isEmpty())
<tr><td class="report-pad" style="padding:18px 30px 8px 30px;">
  <div style="font-size:16px;line-height:23px;font-weight:800;margin-bottom:4px;">▦ Functional coverage</div>
  <div style="font-size:13px;line-height:19px;color:${theme.mutedColor()};margin-bottom:10px;">Pass rate by feature, grouped by report suite.</div>
  @for(var group : view.coverage())
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-bottom:12px;border:1px solid ${theme.borderColor()};border-radius:10px;overflow:hidden;">
      <tr><td style="padding:10px 12px;background:#F8FAFC;font-size:13px;line-height:19px;font-weight:800;">${group.title()} <span style="font-weight:500;color:${theme.mutedColor()};">· ${group.duration()}</span></td></tr>
      @for(var item : group.items())
        <tr><td style="padding:11px 12px;border-top:1px solid ${theme.borderColor()};">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
            <tr>
              <td style="font-size:13px;line-height:19px;font-weight:700;">${item.name()}</td>
              <td width="145" align="right" style="font-size:12px;line-height:18px;color:${theme.mutedColor()};">${item.testCount()} tests · ${item.duration()}</td>
              <td width="56" align="right" style="font-size:13px;line-height:19px;font-weight:800;color:${theme.accent()};">${item.successRate()}</td>
            </tr>
          </table>
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-top:7px;height:7px;background:#E2E8F0;border-radius:999px;overflow:hidden;">
            <tr>
              @if(item.passPercentage() > 0)
                <td width="${item.passPercentage()}%" height="7" style="height:7px;background:${theme.accent()};font-size:0;line-height:0;">&nbsp;</td>
              @endif
              @if(item.passPercentage() < 100)
                <td width="${100 - item.passPercentage()}%" height="7" style="height:7px;background:#E2E8F0;font-size:0;line-height:0;">&nbsp;</td>
              @endif
            </tr>
          </table>
        </td></tr>
      @endfor
    </table>
  @endfor
</td></tr>
@endif
