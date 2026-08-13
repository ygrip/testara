@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
<tr><td class="report-pad" style="padding:24px 30px 10px 30px;">
  <div style="font-size:12px;line-height:17px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:${theme.mutedColor()};margin-bottom:10px;">Executive summary</div>
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
    <tr>
      <td class="stack-cell" width="42%" valign="top" style="padding:0 8px 0 0;">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid ${theme.borderColor()};border-radius:11px;background:#ECFCFB;">
          <tr><td style="padding:17px 18px;">
            <div style="font-size:12px;line-height:17px;font-weight:700;color:#006B66;">Pass rate</div>
            <div style="font-size:34px;line-height:39px;font-weight:800;letter-spacing:-.03em;color:#008B84;margin-top:3px;">${view.status().passRate()}%</div>
            <div style="font-size:13px;line-height:19px;color:${theme.mutedColor()};margin-top:5px;">${view.metadata().totalScenarios()} scenarios across ${view.metadata().totalFeatures()} features</div>
          </td></tr>
        </table>
      </td>
      <td class="stack-cell" width="58%" valign="top" style="padding:0 0 0 8px;">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid ${theme.borderColor()};border-radius:11px;background:#FFFFFF;">
          <tr>
            <td width="33%" style="padding:16px 11px;border-right:1px solid ${theme.borderColor()};">
              <div style="font-size:11px;line-height:16px;color:${theme.mutedColor()};text-transform:uppercase;letter-spacing:.05em;font-weight:700;">Steps</div>
              <div style="font-size:21px;line-height:27px;font-weight:800;margin-top:3px;">${view.metadata().totalSteps()}</div>
            </td>
            <td width="33%" style="padding:16px 11px;border-right:1px solid ${theme.borderColor()};">
              <div style="font-size:11px;line-height:16px;color:${theme.mutedColor()};text-transform:uppercase;letter-spacing:.05em;font-weight:700;">Execution</div>
              <div style="font-size:17px;line-height:27px;font-weight:800;margin-top:3px;">${view.metadata().totalExecutionTime()}</div>
            </td>
            <td width="34%" style="padding:16px 11px;">
              <div style="font-size:11px;line-height:16px;color:${theme.mutedColor()};text-transform:uppercase;letter-spacing:.05em;font-weight:700;">Average</div>
              <div style="font-size:17px;line-height:27px;font-weight:800;margin-top:3px;">${view.metadata().averageExecutionTime()}</div>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>

  <div style="font-size:16px;line-height:23px;font-weight:800;margin-top:22px;margin-bottom:10px;">Test results</div>
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="width:100%;height:12px;background:#E2E8F0;border-radius:999px;overflow:hidden;">
    <tr>
      @if(view.metadata().totalScenarios() == 0)
        <td width="100%" height="12" style="height:12px;background:#E2E8F0;font-size:0;line-height:0;">&nbsp;</td>
      @else
        @for(var metric : view.status().metrics())
          @if(metric.count() > 0)
            <td width="${metric.percentage()}%" height="12" style="height:12px;background:${metric.color()};font-size:0;line-height:0;">&nbsp;</td>
          @endif
        @endfor
      @endif
    </tr>
  </table>

  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-top:10px;">
    <tr>
      @for(var metric : view.status().metrics())
        <td class="metric-cell" width="20%" valign="top" style="padding:0 4px;">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:${metric.background()};border:1px solid ${metric.color()};border-radius:9px;">
            <tr><td style="padding:9px 8px;text-align:center;">
              <div style="font-size:18px;line-height:23px;font-weight:800;color:${metric.color()};">${metric.icon()} ${metric.count()}</div>
              <div style="font-size:12px;line-height:17px;margin-top:2px;color:${metric.color()};">${metric.label()} · ${metric.percentage()}%</div>
            </td></tr>
          </table>
        </td>
      @endfor
    </tr>
  </table>

  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-top:16px;background:#F8FAFC;border-radius:9px;">
    <tr>
      <td style="padding:10px 12px;font-size:13px;line-height:19px;color:${theme.mutedColor()};">
        <strong style="color:${theme.textColor()};">Run window</strong> · ${view.metadata().startTime()} → ${view.metadata().endTime()}<br>
        <strong style="color:${theme.textColor()};">Scenario range</strong> · fastest ${view.metadata().fastestTest()} · slowest ${view.metadata().slowestTest()}
      </td>
    </tr>
  </table>
</td></tr>
