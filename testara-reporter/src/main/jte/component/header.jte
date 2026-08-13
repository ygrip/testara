@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
<tr><td height="5" style="height:5px;line-height:5px;font-size:0;background:${theme.accent()};">&nbsp;</td></tr>
<tr>
  <td class="report-pad" data-email-bento style="padding:28px 30px 27px 30px;background:${theme.headerBackground()};background-image:linear-gradient(125deg,${theme.headerBackground()},#12364a);color:#ffffff;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
      <tr>
        <td valign="top" style="padding-right:18px;">
          @if(view.branding().logoUri() != null)
            <img src="${view.branding().logoUri()}" alt="${view.branding().organizationName()}" width="136" style="display:block;max-width:136px;max-height:40px;width:auto;height:auto;border:0;margin:0 0 14px 0;">
          @endif
          <div style="font-size:11px;line-height:16px;letter-spacing:.10em;font-weight:800;text-transform:uppercase;color:#A8EEE9;">${view.branding().organizationName()}</div>
          <div style="font-size:29px;line-height:35px;font-weight:800;letter-spacing:-.025em;margin-top:5px;color:#ffffff;">${view.metadata().title()}</div>
          <div style="font-size:13px;line-height:19px;margin-top:9px;color:#CBD5E1;">Automation execution report · ${view.metadata().generatedAt()}</div>
          <div style="font-size:12px;line-height:18px;margin-top:14px;color:#D8E4EA;">${view.metadata().totalFeatures()} features · ${view.metadata().totalScenarios()} scenarios · ${view.metadata().totalSteps()} steps · ${view.metadata().totalExecutionTime()}</div>
        </td>
        <td width="164" valign="bottom" align="right">
          <table role="presentation" width="164" cellpadding="0" cellspacing="0" border="0" align="right" style="width:164px;background:#17384A;border:1px solid #33576A;border-radius:15px;">
            <tr><td style="padding:16px 15px;text-align:left;">
              <div style="font-size:10px;line-height:15px;letter-spacing:.08em;text-transform:uppercase;font-weight:800;color:#A8EEE9;">Quality signal</div>
              <div style="font-size:31px;line-height:35px;font-weight:800;letter-spacing:-.035em;color:#ffffff;margin-top:4px;">${view.status().passRate()}%</div>
              <div style="font-size:11px;line-height:16px;color:#CBD5E1;margin-top:2px;">scenario pass rate</div>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin-top:11px;">
                <tr><td align="center" style="padding:5px 8px;background:${view.status().overallBackground()};border:1px solid ${view.status().overallColor()};border-radius:999px;font-size:10px;line-height:14px;font-weight:800;color:${view.status().overallColor()};letter-spacing:.04em;">${view.status().overallStatus()}</td></tr>
              </table>
            </td></tr>
          </table>
        </td>
      </tr>
    </table>
  </td>
</tr>
