@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<section class="overview-grid">
  <article class="surface-card hero-metric">
    <div class="metric-icon-frame">✓</div>
    <div class="metric-label">Pass rate</div>
    <div class="metric-value accent">${view.status().passRate()}%</div>
    <div class="metric-sub">Across ${view.metadata().totalScenarios()} scenarios</div>
    <div class="metric-progress"><span style="width:${view.status().passRate()}%"></span></div>
  </article>
  <article class="surface-card hero-metric">
    <div class="metric-icon-frame info">◷</div>
    <div class="metric-label">Execution time</div>
    <div class="metric-value">${view.metadata().totalExecutionTime()}</div>
    <div class="metric-sub">Average ${view.metadata().averageExecutionTime()} per scenario</div>
  </article>
  <article class="surface-card hero-metric">
    <div class="metric-icon-frame neutral">↯</div>
    <div class="metric-label">Scenario range</div>
    <div class="metric-value compact">${view.metadata().fastestTest()} → ${view.metadata().slowestTest()}</div>
    <div class="metric-sub">Fastest to slowest</div>
  </article>
</section>

<section class="dashboard-grid">
  <article class="surface-card chart-card">
    <div class="section-heading">
      <div><span class="eyebrow dark">Execution status</span><h2>Status distribution</h2></div>
      <span class="status-caption">${view.metadata().totalScenarios()} total</span>
    </div>
    <div class="status-chart-wrap">
      <div class="donut" data-status-donut><div class="donut-center"><strong>${view.status().passRate()}%</strong><span>pass rate</span></div></div>
      <div class="status-legend">
        @for(var metric : view.status().metrics())
          <div class="legend-row" data-status-name="${metric.label().toUpperCase()}" data-status-color="${metric.color()}">
            <span class="legend-dot" style="background:${metric.color()}"></span>
            <span>${metric.label()}</span>
            <strong>${metric.count()}</strong>
            <small>${metric.percentage()}%</small>
          </div>
        @endfor
      </div>
    </div>
  </article>

  <article class="surface-card chart-card">
    <div class="section-heading">
      <div><span class="eyebrow dark">Run context</span><h2>Additional information</h2></div>
    </div>
    @if(view.customFields().isEmpty())
      <div class="empty-compact">No custom report metadata was supplied.</div>
    @else
      <div class="info-grid">
        @for(var field : view.customFields())
          <div class="info-item"><span>${field.label()}</span><strong>${field.value()}</strong></div>
        @endfor
      </div>
    @endif
    <div class="run-window">
      <span><strong>Started</strong>${view.metadata().startTime()}</span>
      <span><strong>Finished</strong>${view.metadata().endTime()}</span>
    </div>
  </article>
</section>

<section class="status-metric-grid">
  @for(var metric : view.status().metrics())
    <article class="surface-card status-metric" style="--metric-color:${metric.color()};--metric-bg:${metric.background()};">
      <div class="metric-strip"></div>
      <div class="status-metric-body">
        <div class="status-metric-top"><span>${metric.label()}</span><span class="status-icon">${metric.icon()}</span></div>
        <strong>${metric.count()}</strong>
        <small>${metric.percentage()}% of scenarios</small>
      </div>
    </article>
  @endfor
</section>
