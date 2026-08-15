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
    <aside class="overall-panel quality-signal" data-quality-signal>
      <div class="quality-head">
        <div>
          <div class="overall-label">Quality signal</div>
          <div class="quality-context">${view.metadata().totalScenarios()} scenarios evaluated</div>
        </div>
        <span class="overall-status status-${view.status().overallStatus()}">${view.status().overallStatus()}</span>
      </div>
      <div class="quality-score-row">
        <div class="overall-value">${view.status().passRate()}%</div>
        <div class="quality-score-copy">scenario<br>pass rate</div>
      </div>
      <div class="quality-track" aria-label="Pass rate ${view.status().passRate()} percent">
        <span style="width:${view.status().passRate()}%;"></span>
      </div>
      <div class="quality-breakdown" data-quality-breakdown>
        @for(var metric : view.status().metrics())
          @if(metric.count() > 0)
            <div class="quality-stat">
              <span class="quality-dot" style="background:${metric.color()};"></span>
              <span>${metric.label()}</span>
              <strong>${metric.count()}</strong>
            </div>
          @endif
        @endfor
      </div>
    </aside>
  </div>
</section>
