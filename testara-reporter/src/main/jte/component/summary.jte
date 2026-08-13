@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
<tr><td style="padding:24px 28px 8px 28px;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
    <tr><td style="padding:16px 18px;background:${view.status().overallBackground()};border-left:4px solid ${view.status().overallColor()};border-radius:8px;">
      <div style="font-size:12px;line-height:16px;color:${theme.mutedColor()};text-transform:uppercase;letter-spacing:.05em;">Overall status</div>
      <div style="font-size:22px;line-height:28px;font-weight:700;color:${view.status().overallColor()};margin-top:3px;">${view.status().overallStatus()}</div>
    </td></tr>
    <tr><td style="padding-top:16px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
        <tr>
          @for(var metric : view.status().metrics())
            <td width="20%" valign="top" style="padding:0 4px;">
              <div style="padding:10px 6px;text-align:center;background:${metric.background()};border-radius:8px;color:${metric.color()};">
                <div style="font-size:16px;font-weight:700;">${metric.icon()} ${metric.count()}</div>
                <div style="font-size:11px;line-height:15px;margin-top:3px;">${metric.label()} · ${metric.percentage()}%</div>
              </div>
            </td>
          @endfor
        </tr>
      </table>
    </td></tr>
    <tr><td style="padding-top:18px;color:${theme.mutedColor()};font-size:12px;line-height:19px;">
      ${view.metadata().totalFeatures()} features · ${view.metadata().totalScenarios()} scenarios · ${view.metadata().totalSteps()} steps<br>
      Started ${view.metadata().startTime()} · Finished ${view.metadata().endTime()}<br>
      Execution ${view.metadata().totalExecutionTime()} · Average ${view.metadata().averageExecutionTime()} · Fastest ${view.metadata().fastestTest()} · Slowest ${view.metadata().slowestTest()}
    </td></tr>
  </table>
</td></tr>
