@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
@if(!view.frequentFailures().isEmpty() || !view.unstableFeatures().isEmpty())
<tr><td style="padding:16px 28px 8px 28px;">
  <div style="font-size:15px;line-height:22px;font-weight:700;margin-bottom:10px;">Failure signals</div>
  @for(var failure : view.frequentFailures())
    <div style="margin-bottom:8px;padding:10px 12px;border:1px solid #fecaca;background:#fef2f2;border-radius:8px;">
      <div style="font-size:12px;font-weight:700;color:#b91c1c;">${failure.name()} · ${failure.count()} occurrence(s)</div>
      <div style="font-size:11px;line-height:16px;color:#7f1d1d;margin-top:3px;word-break:break-word;">${failure.error()}</div>
    </div>
  @endfor
  @if(!view.unstableFeatures().isEmpty())
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-top:10px;border:1px solid ${theme.borderColor()};border-radius:8px;">
      @for(var feature : view.unstableFeatures())
        <tr>
          <td style="padding:8px 10px;border-bottom:1px solid ${theme.borderColor()};font-size:12px;">${feature.name()}</td>
          <td width="100" align="right" style="padding:8px 10px;border-bottom:1px solid ${theme.borderColor()};font-size:12px;font-weight:700;color:#b91c1c;">${feature.failurePercentage()}% failed</td>
        </tr>
      @endfor
    </table>
  @endif
</td></tr>
@endif
