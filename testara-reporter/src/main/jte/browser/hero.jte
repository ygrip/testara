@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<section class="hero" data-glass-card>
  <div class="hero-grid">
    <div>
      <div class="brand-row">
        @if(view.branding().logoUri() != null)
          <img class="brand-logo" src="${view.branding().logoUri()}" alt="${view.branding().organizationName()}">
        @endif
        <span class="brand-name">${view.branding().organizationName()}</span>
      </div>
      <h1>${view.metadata().title()}</h1>
      <p class="hero-copy">Automation execution report with reliability, coverage, performance, and scenario-level diagnostics in one static page.</p>
      <div class="hero-badges">
        <span class="hero-chip">◷ ${view.metadata().totalExecutionTime()} execution</span>
        <span class="hero-chip">▦ ${view.metadata().totalFeatures()} features</span>
        <span class="hero-chip">✓ ${view.metadata().totalScenarios()} scenarios</span>
        <span class="hero-chip">≡ ${view.metadata().totalSteps()} steps</span>
      </div>
    </div>
    <aside class="overall-panel">
      <div class="overall-label">Overall quality signal</div>
      <div class="overall-value">${view.status().passRate()}%</div>
      <div class="overall-caption">Scenario pass rate</div>
      <span class="overall-status status-${view.status().overallStatus()}">${view.status().overallStatus()}</span>
    </aside>
  </div>
</section>
