@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<section class="bento-grid" data-bento-grid>
  <article class="glass-card card-pad metric-card span-4" data-glass-card>
    <div class="metric-top">
      <div>
        <div class="metric-label">Pass rate</div>
        <div class="metric-value teal">${view.status().passRate()}%</div>
      </div>
      <span class="icon-tile">✓</span>
    </div>
    <p class="metric-note">Across ${view.metadata().totalScenarios()} scenarios and ${view.metadata().totalFeatures()} features.</p>
    <div class="mini-progress"><span style="width:${view.status().passRate()}%;"></span></div>
  </article>

  <article class="glass-card card-pad metric-card span-4" data-glass-card>
    <div class="metric-top">
      <div>
        <div class="metric-label">Execution time</div>
        <div class="metric-value">${view.metadata().totalExecutionTime()}</div>
      </div>
      <span class="icon-tile info">◷</span>
    </div>
    <p class="metric-note">Average ${view.metadata().averageExecutionTime()} per scenario.</p>
  </article>

  <article class="glass-card card-pad metric-card span-4" data-glass-card>
    <div class="metric-top">
      <div>
        <div class="metric-label">Scenario range</div>
        <div class="metric-value" style="font-size:23px;">${view.metadata().fastestTest()} → ${view.metadata().slowestTest()}</div>
      </div>
      <span class="icon-tile">↗</span>
    </div>
    <p class="metric-note">Fastest to slowest execution. Large gaps usually expose expensive setup, waits, or downstream latency.</p>
  </article>

  <article class="glass-card card-pad status-chart-card span-8" data-glass-card data-status-chart data-chart-voice>
    <div class="card-head">
      <div>
        <div class="card-eyebrow">Execution status</div>
        <h2 class="card-title">Quality distribution</h2>
        <p class="card-subtitle">A louder view of the run, so failures do not disappear inside a polite table.</p>
      </div>
      <span class="icon-tile">◉</span>
    </div>
    <div class="chart-layout">
      <div>
        <div class="radial-chart" style="--pass:${view.status().passRate()}%;">
          <div class="radial-center">
            <strong>${view.status().passRate()}%</strong>
            <span>pass rate</span>
          </div>
        </div>
        <div class="chart-voice">
          <strong>${view.status().overallStatus()}</strong> · ${view.metadata().totalScenarios()} scenarios executed with ${view.metadata().totalSteps()} total steps.
        </div>
      </div>
      <div>
        <div class="status-stack">
          @if(view.metadata().totalScenarios() == 0)
            <span style="width:100%;background:#DDE6EC;"></span>
          @else
            @for(var metric : view.status().metrics())
              @if(metric.count() > 0)
                <span title="${metric.label()} ${metric.percentage()}%" style="width:${metric.percentage()}%;background:${metric.color()};"></span>
              @endif
            @endfor
          @endif
        </div>
        <div class="legend-list">
          @for(var metric : view.status().metrics())
            <div class="legend-row">
              <span class="legend-dot" style="background:${metric.color()};"></span>
              <span>${metric.label()}</span>
              <b>${metric.count()}</b>
              <small>${metric.percentage()}%</small>
            </div>
          @endfor
        </div>
      </div>
    </div>
  </article>

  <article class="glass-card card-pad span-4" data-glass-card>
    <div class="card-head">
      <div>
        <div class="card-eyebrow">Run context</div>
        <h2 class="card-title">Additional information</h2>
        <p class="card-subtitle">Execution metadata supplied by Testara and your custom fields.</p>
      </div>
      <span class="icon-tile info">i</span>
    </div>
    @if(view.customFields().isEmpty())
      <div class="empty-state">No additional report fields were supplied.</div>
    @else
      <div class="info-grid">
        @for(var field : view.customFields())
          <div class="info-item">
            <span>${field.label()}</span>
            <strong>${field.value()}</strong>
          </div>
        @endfor
      </div>
    @endif
  </article>
</section>
