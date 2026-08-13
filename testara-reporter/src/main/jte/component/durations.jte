@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
@if(!view.longestScenarios().isEmpty() || !view.longestSteps().isEmpty())
<tr><td style="padding:16px 28px 8px 28px;">
  @if(!view.longestScenarios().isEmpty())
    <div style="font-size:15px;line-height:22px;font-weight:700;margin-bottom:8px;">Slowest scenarios</div>
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-bottom:16px;border:1px solid ${theme.borderColor()};border-radius:8px;">
      @for(var item : view.longestScenarios())
        <tr><td style="padding:8px 10px;border-bottom:1px solid ${theme.borderColor()};font-size:12px;">${item.label()}</td><td width="130" align="right" style="padding:8px 10px;border-bottom:1px solid ${theme.borderColor()};font-size:12px;color:${theme.mutedColor()};">${item.duration()}</td></tr>
      @endfor
    </table>
  @endif
  @if(!view.longestSteps().isEmpty())
    <div style="font-size:15px;line-height:22px;font-weight:700;margin-bottom:8px;">Slowest steps</div>
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid ${theme.borderColor()};border-radius:8px;">
      @for(var item : view.longestSteps())
        <tr><td style="padding:8px 10px;border-bottom:1px solid ${theme.borderColor()};font-size:12px;">${item.label()}@if(item.count() != null) <span style="color:${theme.mutedColor()};">· ${item.count()} calls</span>@endif</td><td width="150" align="right" style="padding:8px 10px;border-bottom:1px solid ${theme.borderColor()};font-size:12px;color:${theme.mutedColor()};">${item.duration()}</td></tr>
      @endfor
    </table>
  @endif
</td></tr>
@endif
