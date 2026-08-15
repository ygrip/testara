@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<section class="report-view" data-report-page="overview">
  @template.browser.hero(view)
  <section class="bento-grid">
    <article class="glass-card card-pad metric-card accent-teal span-4" data-glass-card>
      <div class="metric-top"><div><div class="metric-label">Pass rate</div><div class="metric-value teal">${view.status().passRate()}%</div></div><span class="icon-tile">✓</span></div>
      <p class="metric-note">Across ${view.metadata().totalScenarios()} scenarios and ${view.metadata().totalFeatures()} features.</p>
      <div class="mini-progress"><span style="width:${view.status().passRate()}%;"></span></div>
    </article>
    <article class="glass-card card-pad metric-card accent-cyan span-4" data-glass-card>
      <div class="metric-top"><div><div class="metric-label">Execution time</div><div class="metric-value">${view.metadata().totalExecutionTime()}</div></div><span class="icon-tile info">◷</span></div>
      <p class="metric-note">Average ${view.metadata().averageExecutionTime()} per scenario.</p>
    </article>
    <article class="glass-card card-pad metric-card accent-violet span-4" data-glass-card>
      <div class="metric-top"><div><div class="metric-label">Scenario range</div><div class="metric-value compact">${view.metadata().fastestTest()} → ${view.metadata().slowestTest()}</div></div><span class="icon-tile">↗</span></div>
      <p class="metric-note">Fastest to slowest execution, useful for spotting wait-heavy or downstream-bound scenarios.</p>
    </article>
    <article class="glass-card card-pad status-chart-card span-12" data-glass-card data-status-chart data-chart-voice>
      <div class="card-head"><div><div class="card-eyebrow">Execution status</div><h2 class="card-title">Quality distribution</h2><p class="card-subtitle">The run at a glance, with failure weight and scenario distribution kept visually explicit.</p></div><span class="icon-tile">◉</span></div>
      <div class="chart-layout">
        <div class="chart-primary">
          <div class="radial-chart" style="--pass:${view.status().passRate()}%;"><div class="radial-center"><strong>${view.status().passRate()}%</strong><span>scenario pass rate</span></div></div>
          <div class="chart-voice"><strong>${view.status().overallStatus()}</strong> · ${view.metadata().totalScenarios()} scenarios across ${view.metadata().totalFeatures()} features and ${view.metadata().totalSteps()} steps.</div>
        </div>
        <div class="chart-secondary">
          <div class="status-stack" aria-label="Scenario status distribution">
            @if(view.metadata().totalScenarios() == 0)
              <span style="width:100%;background:#DDE6EC;"></span>
            @else
              @for(var metric : view.status().metrics())
                @if(metric.count() > 0)<span title="${metric.label()} ${metric.percentage()}%" style="width:${metric.percentage()}%;background:${metric.color()};"></span>@endif
              @endfor
            @endif
          </div>
          <div class="legend-list">
            @for(var metric : view.status().metrics())
              <div class="legend-row"><span class="legend-dot" style="background:${metric.color()};"></span><span>${metric.label()}</span><b>${metric.count()}</b><small>${metric.percentage()}%</small></div>
            @endfor
          </div>
        </div>
      </div>
    </article>
  </section>
</section>
