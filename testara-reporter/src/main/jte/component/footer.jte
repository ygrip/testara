@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
<tr><td class="report-pad" style="padding:22px 30px 28px 30px;">
  @if(view.metadata().reportLink() != null && !view.metadata().reportLink().isBlank())
    <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin-bottom:18px;"><tr><td style="background:${theme.accent()};border-radius:8px;"><a href="${view.metadata().reportLink()}" style="display:inline-block;padding:11px 17px;color:#ffffff;text-decoration:none;font-size:13px;line-height:18px;font-weight:800;">View full report →</a></td></tr></table>
  @endif
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border-top:1px solid ${theme.borderColor()};">
    <tr>
      <td style="padding-top:16px;font-size:12px;line-height:18px;color:${theme.mutedColor()};">
        <strong style="color:${theme.textColor()};">${view.branding().organizationName()}</strong><br>
        Report generated on ${view.metadata().generatedAt()}
        @if(view.branding().organizationDetail() != null)
          <br>${view.branding().organizationDetail()}
        @endif
      </td>
      <td width="100" align="right" valign="top" style="padding-top:16px;font-size:11px;line-height:17px;color:${theme.mutedColor()};">Automated<br>quality signal</td>
    </tr>
  </table>
</td></tr>
