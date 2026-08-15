@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<section class="bento-grid">
  <article class="glass-card card-pad span-12 results-card" data-glass-card data-visual-blend="setara-ramein">
    @if(view.scenarios().isEmpty())
      <div class="results-heading"><div><div class="card-eyebrow">Execution detail</div><h2 class="card-title">Scenario explorer</h2></div></div>
      <div class="empty-state">Interactive scenario detail is not available for this report.</div>
    @else
      <div class="explorer-stage">
        <div class="scenario-list-view" data-scenario-list-view>
          <div class="results-heading">
            <div>
              <div class="card-eyebrow">Execution detail</div>
              <h2 class="card-title">Scenario explorer</h2>
              <p>Search, filter, and sort the execution table. Select a scenario row to inspect its complete Cucumber detail.</p>
            </div>
            <span class="result-count" data-result-count><strong>${view.scenarios().size()}</strong> / ${view.scenarios().size()} visible</span>
          </div>

          <div class="control-dock clean-explorer-controls" data-control-dock>
            <div class="explorer-search-row">
              <div class="search-shell clean-search-shell">
                <svg class="search-icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="11" cy="11" r="7"></circle>
                  <path d="m20 20-3.5-3.5"></path>
                </svg>
                <input type="search" placeholder="Search scenarios, features, tags, steps, or errors" aria-label="Search scenarios" data-report-search>
                <button class="inline-clear-button" type="button" data-inline-clear data-clear-filters aria-label="Clear search and filters" title="Clear search and filters" hidden>
                  <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 6l12 12"></path><path d="M18 6L6 18"></path></svg>
                </button>
              </div>
            </div>
            <div class="control-secondary explorer-filter-grid" aria-label="Scenario filters">
              <label class="control-label"><span>Status</span><select data-status-filter><option value="">All statuses</option><option value="PASSED">Passed</option><option value="FAILED">Failed</option><option value="SKIPPED">Skipped</option><option value="PENDING">Pending</option><option value="UNDEFINED">Undefined</option></select></label>
              <label class="control-label"><span>Feature</span><select data-feature-filter><option value="">All features</option></select></label>
              <label class="control-label"><span>Suite</span><select data-suite-filter><option value="">All suites</option></select></label>
              <label class="control-label"><span>Tag</span><select data-tag-filter><option value="">All tags</option></select></label>
            </div>
          </div>

          <div class="scenario-table-shell clean-table-shell" data-responsive-table-scroll tabindex="0" aria-label="Scenario results table. Scroll horizontally on narrow screens.">
            <table class="scenario-table clean-scenario-table" data-clean-explorer-table>
              <thead>
                <tr>
                  <th scope="col"><button class="sort-header" type="button" data-sort-column="status">Status <span data-sort-indicator></span></button></th>
                  <th scope="col"><button class="sort-header" type="button" data-sort-column="name">Scenario <span data-sort-indicator></span></button></th>
                  <th scope="col"><button class="sort-header" type="button" data-sort-column="feature">Feature <span data-sort-indicator></span></button></th>
                  <th scope="col">Suite / tags</th>
                  <th scope="col"><button class="sort-header active" type="button" data-sort-column="duration" data-sort-direction="desc">Duration <span data-sort-indicator>↓</span></button></th>
                </tr>
              </thead>
              <tbody data-scenario-body>
                @for(var scenario : view.scenarios())
                  <tr data-scenario-row data-open-scenario data-scenario-id="${scenario.id()}" data-name="${scenario.name()}" data-status="${scenario.status()}" data-feature="${scenario.feature()}" data-suite="${scenario.suite()}" data-tags="${scenario.tags()}" data-duration="${scenario.durationNanos()}" tabindex="0" aria-label="Open scenario details: ${scenario.name()}">
                    <td><span class="status-pill status-${scenario.status().toLowerCase()}">${scenario.status()}</span></td>
                    <td><span class="scenario-name">${scenario.name()}</span><span class="scenario-meta">${scenario.steps().size()} steps@if(scenario.exampleExecution()) · ${scenario.exampleLabel()}@endif</span></td>
                    <td>${scenario.feature()}</td>
                    <td><span>${scenario.suite()}</span>@if(scenario.tags() != null && !scenario.tags().isBlank())<span class="scenario-meta">${scenario.tags()}</span>@endif</td>
                    <td><strong>${scenario.duration()}</strong></td>
                  </tr>
                @endfor
              </tbody>
            </table>
          </div>
          <div class="explorer-empty-state" data-empty-results hidden role="status"><strong>No scenarios match these filters</strong><p>Adjust the search or filters to bring scenarios back into view.</p></div>
        </div>

        <section class="scenario-detail-view" data-scenario-detail-view hidden aria-live="polite">
          <div class="scenario-detail-toolbar">
            <button class="back-button" type="button" data-close-scenario><svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"></path></svg><span>Back to scenarios</span></button>
            <span class="detail-hint">Scenario execution detail</span>
          </div>

          @for(var scenario : view.scenarios())
            <article class="scenario-detail-panel" data-scenario-detail-panel="${scenario.id()}" hidden>
              <header class="scenario-detail-head">
                <div>
                  <div class="scenario-detail-kicker"><span class="status-pill status-${scenario.status().toLowerCase()}">${scenario.status()}</span>@if(scenario.exampleExecution())<span class="example-badge">Scenario Outline · ${scenario.exampleLabel()}</span>@endif</div>
                  <h3>${scenario.name()}</h3>
                  <p>${scenario.feature()} · ${scenario.suite()}</p>
                  @if(scenario.tags() != null && !scenario.tags().isBlank())<div class="detail-tags">${scenario.tags()}</div>@endif
                  <div class="scenario-detail-actions">
                    <button class="detail-info-button" type="button" data-open-step-hooks data-scenario-id="${scenario.id()}" aria-label="View step hooks for ${scenario.name()}">
                      <svg viewBox="0 0 24 24" width="15" height="15" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"></circle><path d="M12 11v5"></path><path d="M12 8h.01"></path></svg>
                      <span>Step hooks</span>
                    </button>
                  </div>
                </div>
                <div class="detail-duration"><span>Duration</span><strong>${scenario.duration()}</strong></div>
              </header>

              @template.browser.scenarioHooks(scenario)
              @template.browser.error("Scenario error", scenario.error())

              <div class="detail-section-head"><div><div class="card-eyebrow">Steps</div><h4>${scenario.steps().size()} executed steps</h4></div></div>
              <div class="step-list rich-step-list">
                @for(var step : scenario.steps())
                  <section class="step-item rich-step-item">
                    <span class="step-dot" style="background:@if(step.status().equalsIgnoreCase("PASSED"))#22C55E@elseif(step.status().equalsIgnoreCase("FAILED"))#EF4444@else#64748B@endif;"></span>
                    <div class="step-content">
                      <div class="step-title-row"><span class="step-number" aria-hidden="true"></span><strong>${step.keyword()} ${step.name()}</strong><small class="step-duration">${step.duration()}</small></div>
                      @if(step.line() != null || (step.matchLocation() != null && !step.matchLocation().isBlank()))
                        <div class="step-source-meta">@if(step.line() != null)<span>Line ${step.line()}</span>@endif @if(step.matchLocation() != null && !step.matchLocation().isBlank())<span>${step.matchLocation()}</span>@endif</div>
                      @endif
                      @template.browser.error("Step error", step.error())
                      @if(step.outputs() != null && !step.outputs().isEmpty())<div class="step-artifact"><div class="artifact-label">Output</div><div class="output-list">@for(var output : step.outputs())<div>${output}</div>@endfor</div></div>@endif
                      @if(step.comments() != null && !step.comments().isEmpty())<div class="step-artifact"><div class="artifact-label">Comments</div><div class="output-list">@for(var comment : step.comments())<div>${comment}</div>@endfor</div></div>@endif

                      @if(step.dataTable() != null && !step.dataTable().isEmpty())
                        <div class="step-artifact"><div class="artifact-label">DataTable</div><div class="step-data-table-shell"><table class="step-data-table"><tbody>
                          @for(var row : step.dataTable())
                            <tr>@for(var cell : row)<td>${cell}</td>@endfor</tr>
                          @endfor
                        </tbody></table></div></div>
                      @endif

                      @if(step.docString() != null)
                        <div class="step-artifact"><div class="artifact-label">Multiline text@if(step.docString().contentType() != null && !step.docString().contentType().isBlank()) · ${step.docString().contentType()}@endif@if(step.docString().line() != null) · line ${step.docString().line()}@endif</div><pre class="step-doc-string">${step.docString().value()}</pre></div>
                      @endif

                      @if(step.attachments() != null && !step.attachments().isEmpty())
                        <div class="step-artifact"><div class="artifact-label">Attachments</div>@template.browser.attachments(step.attachments())</div>
                      @endif
                      @template.browser.hookAttachments(step.beforeHooks())
                      @template.browser.hookAttachments(step.afterHooks())
                    </div>
                  </section>
                @endfor
              </div>
            </article>
          @endfor
        </section>
      </div>
    @endif
  </article>
</section>
