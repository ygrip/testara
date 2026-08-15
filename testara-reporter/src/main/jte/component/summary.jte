@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
<tr><td class="report-pad" style="padding:24px 30px 10px 30px;">
  <div style="font-size:11px;line-height:16px;font-weight:800;letter-spacing:.09em;text-transform:uppercase;color:${theme.mutedColor()};margin-bottom:10px;">Executive summary</div>
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
    <tr>
      <td class="stack-cell" width="45%" valign="top" style="padding:0 8px 0 0;">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid #9ADFD9;border-radius:14px;background:#ECFCFB;">
          <tr><td style="padding:19px 19px 18px 19px;">
            <div style="font-size:11px;line-height:16px;font-weight:800;letter-spacing:.06em;text-transform:uppercase;color:#006B66;">Pass rate</div>
            <div style="font-size:38px;line-height:42px;font-weight:800;letter-spacing:-.04em;color:#008B84;margin-top:4px;">${view.status().passRate()}%</div>
            <div style="font-size:13px;line-height:19px;color:${theme.mutedColor()};margin-top:5px;">${view.metadata().totalScenarios()} scenarios across ${view.metadata().totalFeatures()} features</div>
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-top:13px;height:8px;background:#D5ECEA;border-radius:999px;overflow:hidden;">
              <tr><td width="${view.status().passRate()}%" height="8" style="height:8px;background:#00AFA5;font-size:0;line-height:0;">&nbsp;</td><td height="8" style="height:8px;background:#D5ECEA;font-size:0;line-height:0;">&nbsp;</td></tr>
            </table>
          </td></tr>
        </table>
      </td>
      <td class="stack-cell" width="55%" valign="top" style="padding:0 0 0 8px;">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid ${theme.borderColor()};border-radius:14px;background:#FFFFFF;">
          <tr>
            <td width="50%" style="padding:17px 13px;border-right:1px solid ${theme.borderColor()};border-bottom:1px solid ${theme.borderColor()};">
              <div style="font-size:10px;line-height:15px;color:${theme.mutedColor()};text-transform:uppercase;letter-spacing:.06em;font-weight:800;">Execution</div>
              <div style="font-size:20px;line-height:27px;font-weight:800;margin-top:3px;">${view.metadata().totalExecutionTime()}</div>
            </td>
            <td width="50%" style="padding:17px 13px;border-bottom:1px solid ${theme.borderColor()};">
              <div style="font-size:10px;line-height:15px;color:${theme.mutedColor()};text-transform:uppercase;letter-spacing:.06em;font-weight:800;">Average</div>
              <div style="font-size:20px;line-height:27px;font-weight:800;margin-top:3px;">${view.metadata().averageExecutionTime()}</div>
            </td>
          </tr>
          <tr>
            <td width="50%" style="padding:17px 13px;border-right:1px solid ${theme.borderColor()};">
              <div style="font-size:10px;line-height:15px;color:${theme.mutedColor()};text-transform:uppercase;letter-spacing:.06em;font-weight:800;">Fastest</div>
              <div style="font-size:17px;line-height:24px;font-weight:800;margin-top:3px;color:#008B84;">${view.metadata().fastestTest()}</div>
            </td>
            <td width="50%" style="padding:17px 13px;">
              <div style="font-size:10px;line-height:15px;color:${theme.mutedColor()};text-transform:uppercase;letter-spacing:.06em;font-weight:800;">Slowest</div>
              <div style="font-size:17px;line-height:24px;font-weight:800;margin-top:3px;color:#B45309;">${view.metadata().slowestTest()}</div>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>

  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-top:17px;border:1px solid ${theme.borderColor()};border-radius:13px;background:#F8FAFC;">
    <tr><td style="padding:14px 15px 13px 15px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
        <tr><td style="font-size:16px;line-height:22px;font-weight:800;">Quality distribution</td><td align="right" style="font-size:12px;line-height:18px;color:${theme.mutedColor()};">${view.status().overallStatus()} · ${view.metadata().totalSteps()} steps</td></tr>
      </table>
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-top:10px;width:100%;height:16px;background:#E2E8F0;border-radius:999px;overflow:hidden;">
        <tr>
          @if(view.metadata().totalScenarios() == 0)
            <td width="100%" height="16" style="height:16px;background:#E2E8F0;font-size:0;line-height:0;">&nbsp;</td>
          @else
            @for(var metric : view.status().metrics())
              @if(metric.count() > 0)
                <td width="${metric.percentage()}%" height="16" style="height:16px;background:${metric.color()};font-size:0;line-height:0;">&nbsp;</td>
              @endif
            @endfor
          @endif
        </tr>
      </table>
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-top:11px;">
        <tr>
          @for(var metric : view.status().metrics())
            <td class="metric-cell" width="20%" valign="top" style="padding:0 3px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:${metric.background()};border:1px solid ${metric.color()};border-radius:10px;">
                <tr><td style="padding:9px 7px;text-align:center;">
                  <div style="font-size:17px;line-height:22px;font-weight:800;color:${metric.color()};">${metric.icon()} ${metric.count()}</div>
                  <div style="font-size:11px;line-height:16px;margin-top:2px;color:${metric.color()};">${metric.label()} · ${metric.percentage()}%</div>
                </td></tr>
              </table>
            </td>
          @endfor
        </tr>
      </table>
    </td></tr>
  </table>

  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-top:13px;background:#FFFFFF;border:1px solid ${theme.borderColor()};border-radius:11px;">
    <tr>
      <td width="50%" style="padding:11px 13px;border-right:1px solid ${theme.borderColor()};font-size:12px;line-height:18px;color:${theme.mutedColor()};">
        <strong style="display:block;color:${theme.textColor()};font-size:10px;letter-spacing:.06em;text-transform:uppercase;">Started</strong>${view.metadata().startTime()}
      </td>
      <td width="50%" style="padding:11px 13px;font-size:12px;line-height:18px;color:${theme.mutedColor()};">
        <strong style="display:block;color:${theme.textColor()};font-size:10px;letter-spacing:.06em;text-transform:uppercase;">Finished</strong>${view.metadata().endTime()}
      </td>
    </tr>
  </table>
</td></tr>
