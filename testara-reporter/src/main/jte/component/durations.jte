@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
@if(!view.longestScenarios().isEmpty() || !view.longestSteps().isEmpty())
<tr><td class="report-pad" style="padding:18px 30px 8px 30px;">
  <div style="font-size:16px;line-height:23px;font-weight:800;margin-bottom:4px;">◷ Performance signals</div>
  <div style="font-size:13px;line-height:19px;color:${theme.mutedColor()};margin-bottom:11px;">The slowest paths in this run, normalized against the longest item.</div>
  @if(!view.longestScenarios().isEmpty())
    <div style="font-size:14px;line-height:20px;font-weight:800;margin-bottom:8px;">Slowest scenarios</div>
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-bottom:15px;border:1px solid ${theme.borderColor()};border-radius:10px;overflow:hidden;">
      @for(var item : view.longestScenarios())
        <tr><td style="padding:10px 11px;border-bottom:1px solid ${theme.borderColor()};">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"><tr>
            <td style="font-size:13px;line-height:19px;font-weight:650;">${item.label()}</td>
            <td width="120" align="right" style="font-size:12px;line-height:18px;color:${theme.mutedColor()};font-weight:700;">${item.duration()}</td>
          </tr></table>
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-top:6px;height:6px;background:#E2E8F0;border-radius:999px;overflow:hidden;"><tr>
            <td width="${item.percentage()}%" height="6" style="height:6px;background:${theme.accent()};font-size:0;line-height:0;">&nbsp;</td>
            @if(item.percentage() < 100)
              <td width="${100 - item.percentage()}%" height="6" style="height:6px;background:#E2E8F0;font-size:0;line-height:0;">&nbsp;</td>
            @endif
          </tr></table>
        </td></tr>
      @endfor
    </table>
  @endif
  @if(!view.longestSteps().isEmpty())
    <div style="font-size:14px;line-height:20px;font-weight:800;margin-bottom:8px;">Slowest steps</div>
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid ${theme.borderColor()};border-radius:10px;overflow:hidden;">
      @for(var item : view.longestSteps())
        <tr><td style="padding:10px 11px;border-bottom:1px solid ${theme.borderColor()};">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"><tr>
            <td style="font-size:13px;line-height:19px;font-weight:650;">${item.label()}@if(item.count() != null) <span style="color:${theme.mutedColor()};font-weight:500;">· ${item.count()} calls</span>@endif</td>
            <td width="150" align="right" style="font-size:12px;line-height:18px;color:${theme.mutedColor()};font-weight:700;">${item.duration()}</td>
          </tr></table>
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-top:6px;height:6px;background:#E2E8F0;border-radius:999px;overflow:hidden;"><tr>
            <td width="${item.percentage()}%" height="6" style="height:6px;background:#3B82F6;font-size:0;line-height:0;">&nbsp;</td>
            @if(item.percentage() < 100)
              <td width="${100 - item.percentage()}%" height="6" style="height:6px;background:#E2E8F0;font-size:0;line-height:0;">&nbsp;</td>
            @endif
          </tr></table>
        </td></tr>
      @endfor
    </table>
  @endif
</td></tr>
@endif
