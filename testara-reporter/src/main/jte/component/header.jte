@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
<tr><td height="4" style="height:4px;line-height:4px;font-size:0;background:${theme.accent()};">&nbsp;</td></tr>
<tr>
  <td class="report-pad" style="padding:28px 30px 26px 30px;background:${theme.headerBackground()};color:#ffffff;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
      <tr>
        <td valign="top">
          @if(view.branding().logoUri() != null)
            <img src="${view.branding().logoUri()}" alt="${view.branding().organizationName()}" width="132" style="display:block;max-width:132px;max-height:40px;width:auto;height:auto;border:0;margin:0 0 13px 0;">
          @endif
          <div style="font-size:12px;line-height:17px;letter-spacing:.08em;font-weight:700;text-transform:uppercase;color:#A8EEE9;">${view.branding().organizationName()}</div>
          <div style="font-size:27px;line-height:34px;font-weight:750;letter-spacing:-.02em;margin-top:5px;color:#ffffff;">${view.metadata().title()}</div>
          <div style="font-size:13px;line-height:19px;margin-top:8px;color:#CBD5E1;">Automation execution report · ${view.metadata().generatedAt()}</div>
        </td>
        <td width="118" valign="top" align="right" style="padding-left:16px;">
          <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="right">
            <tr><td align="center" style="padding:8px 12px;background:${view.status().overallBackground()};border:1px solid ${view.status().overallColor()};border-radius:999px;font-size:12px;line-height:16px;font-weight:800;color:${view.status().overallColor()};letter-spacing:.04em;">${view.status().overallStatus()}</td></tr>
          </table>
        </td>
      </tr>
    </table>
  </td>
</tr>
