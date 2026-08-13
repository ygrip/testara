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
            <div><div class="card-eyebrow">Execution detail</div><h2 class="card-title">Scenario explorer</h2><p>Search and filter the run. Select any row to inspect its complete Cucumber execution detail.</p></div>
            <span class="result-count" data-result-count><strong>${view.scenarios().size()}</strong> / ${view.scenarios().size()} visible</span>
          </div>

          <div class="control-dock" data-control-dock>
            <div class="control-primary explorer-primary">
              <div class="search-shell">
                <svg class="search-icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7"></circle><path d="m20 20-3.5-3.5"></path></svg>
                <input type="search" placeholder="Search scenario, feature, suite, tag, step, or error…" aria-label="Search report" data-report-search>
              </div>
              <button class="clear-icon-button" type="button" data-clear-filters aria-label="Clear search and filters" title="Clear search and filters">
                <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 1 0 3-6.7"></path><path d="M3 4v6h6"></path></svg>
              </button>
            </div>
            <div class="control-secondary explorer-filter-rail">
              <label class="control-label"><span>Status</span><select data-status-filter><option value="">All statuses</option><option value="PASSED">Passed</option><option value="FAILED">Failed</option><option value="SKIPPED">Skipped</option><option value="PENDING">Pending</option><option value="UNDEFINED">Undefined</option></select></label>
              <label class="control-label"><span>Feature</span><select data-feature-filter><option value="">All features</option></select></label>
              <label class="control-label"><span>Suite</span><select data-suite-filter><option value="">All suites</option></select></label>
              <label class="control-label"><span>Tag</span><select data-tag-filter><option value="">All tags</option></select></label>
            </div>
          </div>

          <div class="mobile-sort-strip" data-mobile-sort-strip aria-label="Sort scenarios">
            <span class="mobile-sort-label">Sort</span>
            <button class="sort-header mobile-sort-button" type="button" data-sort-column="status" aria-pressed="false">Status <span data-sort-indicator></span></button>
            <button class="sort-header mobile-sort-button" type="button" data-sort-column="name" aria-pressed="false">Scenario <span data-sort-indicator></span></button>
            <button class="sort-header mobile-sort-button" type="button" data-sort-column="feature" aria-pressed="false">Feature <span data-sort-indicator></span></button>
            <button class="sort-header mobile-sort-button active" type="button" data-sort-column="duration" data-sort-direction="desc" aria-pressed="true">Duration <span data-sort-indicator>↓</span></button>
          </div>

          <div class="scenario-table-shell">
            <table class="scenario-table">
              <thead><tr>
                <th><button class="sort-header" type="button" data-sort-column="status">Status <span data-sort-indicator></span></button></th>
                <th><button class="sort-header" type="button" data-sort-column="name">Scenario <span data-sort-indicator></span></button></th>
                <th><button class="sort-header" type="button" data-sort-column="feature">Feature <span data-sort-indicator></span></button></th>
                <th>Suite / tags</th>
                <th><button class="sort-header active" type="button" data-sort-column="duration" data-sort-direction="desc">Duration <span data-sort-indicator>↓</span></button></th>
              </tr></thead>
              <tbody data-scenario-body>
                @for(var scenario : view.scenarios())
                  <tr data-scenario-row data-open-scenario data-scenario-id="${scenario.id()}" data-name="${scenario.name()}" data-status="${scenario.status()}" data-feature="${scenario.feature()}" data-suite="${scenario.suite()}" data-tags="${scenario.tags()}" data-duration="${scenario.durationNanos()}" tabindex="0" role="button" aria-label="Open scenario details: ${scenario.name()}">
                    <td data-label="Status"><span class="status-pill status-${scenario.status().toLowerCase()}">${scenario.status()}</span></td>
                    <td data-label="Scenario"><span class="scenario-name">${scenario.name()}</span><span class="scenario-meta">${scenario.steps().size()} steps@if(scenario.exampleExecution()) · ${scenario.exampleLabel()}@endif</span></td>
                    <td data-label="Feature">${scenario.feature()}</td>
                    <td data-label="Suite / tags"><span>${scenario.suite()}</span>@if(scenario.tags() != null && !scenario.tags().isBlank())<span class="scenario-meta">${scenario.tags()}</span>@endif</td>
                    <td data-label="Duration"><strong>${scenario.duration()}</strong></td>
                  </tr>
                @endfor
              </tbody>
            </table>
          </div>
        </div>

        <section class="scenario-detail-view" data-scenario-detail-view hidden aria-live="polite">
          <div class="scenario-detail-toolbar">
            <button class="back-button" type="button" data-close-scenario>
              <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"></path></svg>
              <span>Back to scenarios</span>
            </button>
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
                </div>
                <div class="detail-duration"><span>Duration</span><strong>${scenario.duration()}</strong></div>
              </header>

              @if(scenario.error() != null && !scenario.error().isBlank())
                <pre class="error-box scenario-error">${scenario.error()}</pre>
              @endif

              <div class="detail-section-head"><div><div class="card-eyebrow">Steps</div><h4>${scenario.steps().size()} executed steps</h4></div></div>
              <div class="step-list rich-step-list">
                @for(var step : scenario.steps())
                  <section class="step-item rich-step-item">
                    <span class="step-dot" style="background:@if(step.status().equalsIgnoreCase("PASSED"))#22C55E@elseif(step.status().equalsIgnoreCase("FAILED"))#EF4444@else#64748B@endif;"></span>
                    <div class="step-content">
                      <div class="step-title-row"><strong>${step.keyword()} ${step.name()}</strong><small class="step-duration">${step.duration()}</small></div>
                      @if(step.error() != null && !step.error().isBlank())<pre class="error-box step-error">${step.error()}</pre>@endif

                      @if(step.dataTable() != null && !step.dataTable().isEmpty())
                        <div class="step-artifact"><div class="artifact-label">DataTable</div><div class="step-data-table-shell"><table class="step-data-table"><tbody>
                          @for(var row : step.dataTable())
                            <tr>@for(var cell : row)<td>${cell}</td>@endfor</tr>
                          @endfor
                        </tbody></table></div></div>
                      @endif

                      @if(step.docString() != null && !step.docString().isBlank())
                        <div class="step-artifact"><div class="artifact-label">Multiline text</div><pre class="step-doc-string">${step.docString()}</pre></div>
                      @endif

                      @if(step.attachments() != null && !step.attachments().isEmpty())
                        <div class="step-artifact"><div class="artifact-label">Attachments</div><div class="attachment-grid">
                          @for(var attachment : step.attachments())
                            @if(attachment.kind().equals("image"))
                              <figure class="attachment-card"><img class="attachment-image" src="${attachment.uri()}" alt="${attachment.name()}" loading="lazy"><figcaption>${attachment.name()}</figcaption></figure>
                            @elseif(attachment.kind().equals("video"))
                              <figure class="attachment-card"><video class="attachment-video" controls preload="metadata"><source src="${attachment.uri()}" type="${attachment.mimeType()}"></video><figcaption>${attachment.name()}</figcaption></figure>
                            @elseif(attachment.kind().equals("audio"))
                              <div class="attachment-card attachment-audio"><audio controls preload="metadata"><source src="${attachment.uri()}" type="${attachment.mimeType()}"></audio><span>${attachment.name()}</span></div>
                            @else
                              <a class="attachment-file" href="${attachment.uri()}" download="${attachment.name()}">${attachment.name()} · ${attachment.mimeType()}</a>
                            @endif
                          @endfor
                        </div></div>
                      @endif
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
