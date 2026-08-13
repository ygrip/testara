@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
@if(!view.frequentFailures().isEmpty() || !view.unstableFeatures().isEmpty())
<tr><td class="report-pad" style="padding:18px 30px 8px 30px;">
  <div style="font-size:16px;line-height:23px;font-weight:800;margin-bottom:4px;">! Failure signals</div>
  <div style="font-size:13px;line-height:19px;color:${theme.mutedColor()};margin-bottom:11px;">Frequent errors and unstable features that deserve attention first.</div>
  @if(!view.frequentFailures().isEmpty())
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid #FECACA;border-radius:10px;overflow:hidden;background:#FFFFFF;">
      @for(var failure : view.frequentFailures())
        <tr><td style="padding:11px 12px;border-bottom:1px solid #FEE2E2;">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
            <tr>
              <td style="font-size:13px;line-height:19px;font-weight:800;color:#991B1B;">${failure.name()}</td>
              <td width="92" align="right" style="font-size:12px;line-height:18px;font-weight:800;color:#DC2626;">${failure.count()} occurrence(s)</td>
            </tr>
          </table>
          <div style="font-size:12px;line-height:18px;color:#7F1D1D;margin-top:3px;word-break:break-word;">${failure.error()}</div>
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-top:7px;height:6px;background:#FEE2E2;border-radius:999px;overflow:hidden;">
            <tr>
              <td width="${failure.relativePercentage()}%" height="6" style="height:6px;background:#EF4444;font-size:0;line-height:0;">&nbsp;</td>
              @if(failure.relativePercentage() < 100)
                <td width="${100 - failure.relativePercentage()}%" height="6" style="height:6px;background:#FEE2E2;font-size:0;line-height:0;">&nbsp;</td>
              @endif
            </tr>
          </table>
        </td></tr>
      @endfor
    </table>
  @endif

  @if(!view.unstableFeatures().isEmpty())
    <div style="font-size:14px;line-height:20px;font-weight:800;margin:15px 0 8px 0;">Most unstable features</div>
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid ${theme.borderColor()};border-radius:10px;overflow:hidden;">
      @for(var feature : view.unstableFeatures())
        <tr><td style="padding:9px 11px;border-bottom:1px solid ${theme.borderColor()};">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"><tr>
            <td style="font-size:13px;line-height:19px;font-weight:650;">${feature.name()}</td>
            <td width="76" align="right" style="font-size:12px;line-height:18px;font-weight:800;color:#DC2626;">${feature.failurePercentage()}% failed</td>
          </tr></table>
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-top:6px;height:5px;background:#E2E8F0;border-radius:999px;overflow:hidden;"><tr>
            @if(feature.failurePercentage() > 0)
              <td width="${feature.failurePercentage()}%" height="5" style="height:5px;background:#EF4444;font-size:0;line-height:0;">&nbsp;</td>
            @endif
            @if(feature.failurePercentage() < 100)
              <td width="${100 - feature.failurePercentage()}%" height="5" style="height:5px;background:#E2E8F0;font-size:0;line-height:0;">&nbsp;</td>
            @endif
          </tr></table>
        </td></tr>
      @endfor
    </table>
  @endif
</td></tr>
@endif
