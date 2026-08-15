@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<section class="surface-card results-card">
  <div class="results-heading">
    <div>
      <span class="eyebrow dark">Execution detail</span>
      <h2>Test results</h2>
      <p>Search, filter, sort, and inspect every scenario without a server.</p>
    </div>
    <span class="result-count"><strong data-visible-count>${view.scenarios().size()}</strong> / ${view.scenarios().size()} visible</span>
  </div>

  <div class="filter-toolbar" role="search" aria-label="Filter report scenarios">
    <label class="search-control">
      <span>Search</span>
      <div class="search-box"><span aria-hidden="true">⌕</span><input type="search" data-report-search placeholder="Scenario, feature, tag, error, step…" autocomplete="off"></div>
    </label>
    <label><span>Status</span><select data-status-filter><option value="">All statuses</option></select></label>
    <label><span>Suite</span><select data-suite-filter><option value="">All suites</option></select></label>
    <label><span>Feature</span><select data-feature-filter><option value="">All features</option></select></label>
    <label><span>Tag</span><select data-tag-filter><option value="">All tags</option></select></label>
    <label><span>Sort</span><select data-report-sort>
      <option value="duration-desc">Duration · slowest</option>
      <option value="duration-asc">Duration · fastest</option>
      <option value="name-asc">Scenario · A–Z</option>
      <option value="status-asc">Status</option>
    </select></label>
    <label class="toggle-control"><input type="checkbox" data-failed-only><span class="toggle-ui"></span><span>Failed only</span></label>
    <button class="clear-button" type="button" data-clear-filters>Clear</button>
  </div>

  <div class="table-scroll">
    <table class="results-table">
      <thead><tr><th>Status</th><th>Scenario</th><th>Feature / suite</th><th>Tags</th><th class="right">Duration</th><th class="right">Details</th></tr></thead>
      <tbody data-scenario-body>
        @for(var scenario : view.scenarios())
          <tr data-scenario-row data-id="${scenario.id()}" data-status="${scenario.status()}" data-suite="${scenario.suite()}" data-feature="${scenario.feature()}" data-tags="${scenario.tags()}" data-duration="${scenario.durationNanos()}">
            <td><span class="scenario-status status-${scenario.status().toLowerCase()}">${scenario.status()}</span></td>
            <td><strong>${scenario.name()}</strong></td>
            <td><span>${scenario.feature()}</span><small>${scenario.suite()}</small></td>
            <td>@if(scenario.tags() == null || scenario.tags().isBlank())<span class="muted">—</span>@else<span class="tag-list">${scenario.tags()}</span>@endif</td>
            <td class="right mono">${scenario.duration()}</td>
            <td class="right"><button type="button" class="detail-button" data-detail-toggle="${scenario.id()}" aria-expanded="false">View</button></td>
          </tr>
          <tr data-detail-row data-detail-for="${scenario.id()}" hidden>
            <td colspan="6" class="scenario-detail-cell">
              <div class="scenario-detail">
                <div class="detail-head">
                  <div><span class="eyebrow dark">Scenario details</span><h3>${scenario.name()}</h3></div>
                  <span class="scenario-status status-${scenario.status().toLowerCase()}">${scenario.status()}</span>
                </div>
                <div class="detail-meta"><span><strong>Feature</strong>${scenario.feature()}</span><span><strong>Suite</strong>${scenario.suite()}</span><span><strong>Duration</strong>${scenario.duration()}</span>@if(scenario.tags() != null && !scenario.tags().isBlank())<span><strong>Tags</strong>${scenario.tags()}</span>@endif</div>
                @if(scenario.error() != null && !scenario.error().isBlank())
                  <div class="error-panel"><div class="error-title"><span>!</span>Failure detail</div><pre>${scenario.error()}</pre></div>
                @endif
                <div class="step-list">
                  @for(var step : scenario.steps())
                    <div class="step-row">
                      <span class="step-dot status-bg-${step.status().toLowerCase()}"></span>
                      <div class="step-copy"><span class="step-keyword">${step.keyword()}</span><strong>${step.name()}</strong>@if(step.error() != null && !step.error().isBlank())<pre>${step.error()}</pre>@endif</div>
                      <span class="step-duration mono">${step.duration()}</span>
                    </div>
                  @endfor
                </div>
              </div>
            </td>
          </tr>
        @endfor
      </tbody>
    </table>
  </div>
  <div class="empty-results" data-empty-results hidden><span>⌕</span><strong>No scenarios match these filters.</strong><small>Clear a filter or broaden the search.</small></div>
</section>
