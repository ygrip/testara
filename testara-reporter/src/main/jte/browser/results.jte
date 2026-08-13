@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<section class="bento-grid">
  <article class="glass-card card-pad span-12 results-card" data-glass-card>
    <div class="results-heading">
      <div>
        <div class="card-eyebrow">Execution detail</div>
        <h2 class="card-title">Scenario explorer</h2>
        <p>Search, filter, sort, and inspect the run without a server or external asset.</p>
      </div>
      <span class="result-count" data-result-count><strong>${view.scenarios().size()}</strong> / ${view.scenarios().size()} visible</span>
    </div>

    <div class="control-dock" data-control-dock>
      <div class="control-primary">
        <div class="search-shell">
          <span class="search-icon">⌕</span>
          <input type="search" placeholder="Search scenario, feature, suite, tag, step, or error…" aria-label="Search report" data-report-search>
        </div>
        <label class="failed-chip" title="Show only failed scenarios">
          <input type="checkbox" data-failed-only>
          <span>Failed only</span>
        </label>
        <button class="filter-button secondary" type="button" data-clear-filters>Clear filters</button>
      </div>
      <div class="control-secondary">
        <label class="control-label">
          <span>Status</span>
          <select data-status-filter>
            <option value="">All statuses</option>
            <option value="PASSED">Passed</option>
            <option value="FAILED">Failed</option>
            <option value="SKIPPED">Skipped</option>
            <option value="PENDING">Pending</option>
            <option value="UNDEFINED">Undefined</option>
          </select>
        </label>
        <label class="control-label">
          <span>Feature</span>
          <select data-feature-filter><option value="">All features</option></select>
        </label>
        <label class="control-label">
          <span>Suite</span>
          <select data-suite-filter><option value="">All suites</option></select>
        </label>
        <label class="control-label">
          <span>Tag</span>
          <select data-tag-filter><option value="">All tags</option></select>
        </label>
        <label class="control-label">
          <span>Sort</span>
          <select data-report-sort>
            <option value="duration-desc">Duration · slowest</option>
            <option value="duration-asc">Duration · fastest</option>
            <option value="name-asc">Scenario · A–Z</option>
            <option value="status">Status</option>
          </select>
        </label>
      </div>
    </div>

    @if(view.scenarios().isEmpty())
      <div class="empty-state">Interactive scenario detail is not available for this report.</div>
    @else
      <div style="overflow-x:auto;">
        <table class="scenario-table">
          <thead>
            <tr><th>Status</th><th>Scenario</th><th>Feature</th><th>Suite / tags</th><th>Duration</th><th></th></tr>
          </thead>
          <tbody data-scenario-body>
            @for(var scenario : view.scenarios())
              <tr data-scenario-row data-scenario-id="${scenario.id()}" data-status="${scenario.status()}" data-feature="${scenario.feature()}" data-suite="${scenario.suite()}" data-tags="${scenario.tags()}" data-duration="${scenario.durationNanos()}">
                <td data-label="Status"><span class="status-pill status-${scenario.status().toLowerCase()}">${scenario.status()}</span></td>
                <td data-label="Scenario"><span class="scenario-name">${scenario.name()}</span><span class="scenario-meta">${scenario.steps().size()} steps</span></td>
                <td data-label="Feature">${scenario.feature()}</td>
                <td data-label="Suite / tags"><span>${scenario.suite()}</span>@if(scenario.tags() != null && !scenario.tags().isBlank())<span class="scenario-meta">${scenario.tags()}</span>@endif</td>
                <td data-label="Duration"><strong>${scenario.duration()}</strong></td>
                <td><button class="detail-button" type="button" data-detail-toggle>View details</button></td>
              </tr>
              <tr class="detail-row" data-detail-row hidden>
                <td colspan="6">
                  <div class="detail-panel">
                    <div class="card-eyebrow">Scenario details</div>
                    <div class="detail-grid" style="margin-top:10px;">
                      <div>
                        <div class="step-list">
                          @for(var step : scenario.steps())
                            <div class="step-item">
                              <span class="step-dot" style="background:@if(step.status().equalsIgnoreCase("PASSED"))#22C55E@elseif(step.status().equalsIgnoreCase("FAILED"))#EF4444@else#64748B@endif;"></span>
                              <div>
                                <strong style="font-size:12px;">${step.keyword()} ${step.name()}</strong>
                                @if(step.error() != null && !step.error().isBlank())
                                  <small style="display:block;color:#B91C1C;margin-top:2px;">${step.error()}</small>
                                @endif
                              </div>
                              <small style="color:var(--muted);white-space:nowrap;">${step.duration()}</small>
                            </div>
                          @endfor
                        </div>
                      </div>
                      <div>
                        @if(scenario.error() != null && !scenario.error().isBlank())
                          <pre class="error-box">${scenario.error()}</pre>
                        @else
                          <div class="empty-state">No scenario-level error was recorded.</div>
                        @endif
                      </div>
                    </div>
                  </div>
                </td>
              </tr>
            @endfor
          </tbody>
        </table>
      </div>
    @endif
  </article>
</section>
