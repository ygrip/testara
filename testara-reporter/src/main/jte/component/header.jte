@import io.github.ygrip.testara.reporter.model.ReportTheme
@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@param ReportTheme theme
<tr>
  <td style="padding:26px 28px;background:${theme.headerBackground()};color:#ffffff;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
      <tr>
        <td valign="middle">
          @if(view.branding().logoUri() != null)
            <img src="${view.branding().logoUri()}" alt="${view.branding().organizationName()}" width="140" style="display:block;max-width:140px;max-height:44px;width:auto;height:auto;border:0;margin:0 0 12px 0;">
          @endif
          <div style="font-size:13px;line-height:18px;letter-spacing:.04em;font-weight:700;opacity:.85;">${view.branding().organizationName()}</div>
          <div style="font-size:25px;line-height:32px;font-weight:700;margin-top:4px;">${view.metadata().title()}</div>
        </td>
      </tr>
    </table>
  </td>
</tr>
