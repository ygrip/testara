@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<footer class="report-footer">
  <strong>${view.branding().organizationName()}</strong> · generated ${view.metadata().generatedAt()}
  @if(view.branding().organizationDetail() != null)
    <br>${view.branding().organizationDetail()}
  @endif
</footer>
