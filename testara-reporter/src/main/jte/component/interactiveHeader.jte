@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<header class="hero surface-card">
  <div class="hero-accent"></div>
  <div class="hero-top">
    <div class="brand-lockup">
      @if(view.branding().logoUri() != null)
        <img class="brand-logo" src="${view.branding().logoUri()}" alt="${view.branding().organizationName()}">
      @endif
      <div>
        <div class="eyebrow">${view.branding().organizationName()}</div>
        <h1>${view.metadata().title()}</h1>
        <p class="hero-subtitle">Automation execution report · generated ${view.metadata().generatedAt()}</p>
      </div>
    </div>
    <span class="overall-badge overall-${view.status().overallStatus().toLowerCase()}">${view.status().overallStatus()}</span>
  </div>
  <div class="hero-meta">
    <span>◷ ${view.metadata().totalExecutionTime()} execution</span>
    <span>▦ ${view.metadata().totalFeatures()} features</span>
    <span>✓ ${view.metadata().totalScenarios()} scenarios</span>
    <span>≡ ${view.metadata().totalSteps()} steps</span>
  </div>
</header>
