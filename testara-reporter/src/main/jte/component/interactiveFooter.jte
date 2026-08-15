@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<footer class="interactive-footer">
  <div>
    <strong>${view.branding().organizationName()}</strong>
    <span>Report generated ${view.metadata().generatedAt()}</span>
    @if(view.branding().organizationDetail() != null)
      <span>${view.branding().organizationDetail()}</span>
    @endif
  </div>
  <div class="footer-note">Static Testara report · no server required</div>
</footer>
