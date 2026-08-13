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
        <div class="metric-value compact">${view.metadata().fastestTest()} → ${view.metadata().slowestTest()}</div>
      </div>
      <span class="icon-tile">↗</span>
    </div>
    <p class="metric-note">Fastest to slowest execution, useful for spotting wait-heavy or downstream-bound scenarios.</p>
  </article>

  <article class="glass-card card-pad status-chart-card span-12" data-glass-card data-status-chart data-chart-voice>
    <div class="card-head">
      <div>
        <div class="card-eyebrow">Execution status</div>
        <h2 class="card-title">Quality distribution</h2>
        <p class="card-subtitle">The run at a glance, with failure weight and scenario distribution kept visually explicit.</p>
      </div>
      <span class="icon-tile">◉</span>
    </div>
    <div class="chart-layout">
      <div class="chart-primary">
        <div class="radial-chart" style="--pass:${view.status().passRate()}%;">
          <div class="radial-center">
            <strong>${view.status().passRate()}%</strong>
            <span>scenario pass rate</span>
          </div>
        </div>
        <div class="chart-voice">
          <strong>${view.status().overallStatus()}</strong> · ${view.metadata().totalScenarios()} scenarios across ${view.metadata().totalFeatures()} features and ${view.metadata().totalSteps()} steps.
        </div>
      </div>
      <div class="chart-secondary">
        <div class="status-stack" aria-label="Scenario status distribution">
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

  <article class="glass-card card-pad span-12" data-glass-card>
    <div class="card-head compact-head">
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
      <div class="context-table-shell">
        <table class="context-table" data-run-context-table>
          <tbody>
            @for(var field : view.customFields())
              <tr>
                <th scope="row">${field.label()}</th>
                <td>${field.value()}</td>
              </tr>
            @endfor
          </tbody>
        </table>
      </div>
    @endif
  </article>
</section>
