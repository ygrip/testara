@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<section class="bento-grid">
  <article class="glass-card card-pad span-12" data-glass-card data-full-row="failure-signals">
    <div class="card-head">
      <div>
        <div class="card-eyebrow danger-eyebrow">Reliability</div>
        <h2 class="card-title">Failure signals</h2>
        <p class="card-subtitle">Recurring failures and unstable features, ranked so the largest reliability risks are obvious first.</p>
      </div>
      <span class="icon-tile danger">!</span>
    </div>
    @if(view.frequentFailures().isEmpty())
      <div class="empty-state">No recurring failure signatures were detected.</div>
    @else
      <div class="rank-list wide-rank-list">
        @for(var failure : view.frequentFailures())
          <div class="rank-item signal-row">
            <div class="rank-head">
              <span>${failure.name()}</span>
              <strong class="danger-value">${failure.count()}×</strong>
            </div>
            <div class="bar-track danger"><span style="width:${failure.relativePercentage()}%;"></span></div>
            <small>${failure.error()}</small>
          </div>
        @endfor
      </div>
    @endif
    @if(!view.unstableFeatures().isEmpty())
      <div class="subsection-block">
        <div class="card-eyebrow warning-eyebrow">Unstable features</div>
        <div class="rank-list wide-rank-list subsection-list">
          @for(var feature : view.unstableFeatures())
            <div class="rank-item">
              <div class="rank-head"><span>${feature.name()}</span><strong>${feature.failurePercentage()}% failed</strong></div>
              <div class="bar-track danger"><span style="width:${feature.failurePercentage()}%;"></span></div>
            </div>
          @endfor
        </div>
      </div>
    @endif
  </article>

  <article class="glass-card card-pad span-12" data-glass-card data-full-row="execution-hotspots">
    <div class="card-head">
      <div>
        <div class="card-eyebrow">Performance</div>
        <h2 class="card-title">Execution hotspots</h2>
        <p class="card-subtitle">Slow scenarios and step families ranked by relative duration, with enough horizontal space to compare outliers properly.</p>
      </div>
      <span class="icon-tile info">↗</span>
    </div>
    @if(view.longestScenarios().isEmpty())
      <div class="empty-state">No scenario duration data is available.</div>
    @else
      <div class="rank-list wide-rank-list">
        @for(var item : view.longestScenarios())
          <div class="rank-item">
            <div class="rank-head"><span>${item.label()}</span><strong>${item.duration()}</strong></div>
            <div class="bar-track"><span style="width:${item.percentage()}%;"></span></div>
          </div>
        @endfor
      </div>
    @endif
    @if(!view.longestSteps().isEmpty())
      <div class="subsection-block">
        <div class="card-eyebrow">Slow step families</div>
        <div class="rank-list wide-rank-list subsection-list">
          @for(var item : view.longestSteps())
            <div class="rank-item">
              <div class="rank-head"><span>${item.label()}</span><strong>${item.duration()}</strong></div>
              <div class="bar-track"><span style="width:${item.percentage()}%;"></span></div>
              @if(item.count() != null)
                <small>${item.count()} executions</small>
              @endif
            </div>
          @endfor
        </div>
      </div>
    @endif
  </article>

  <article class="glass-card card-pad span-12" data-glass-card data-full-row="functional-coverage">
    <div class="card-head">
      <div>
        <div class="card-eyebrow">Coverage</div>
        <h2 class="card-title">Functional coverage</h2>
        <p class="card-subtitle">Feature pass rates and execution density across the full report width, grouped by the suites Testara inferred from your Cucumber structure.</p>
      </div>
      <span class="icon-tile">▦</span>
    </div>
    @if(view.coverage().isEmpty())
      <div class="empty-state">No feature coverage data is available.</div>
    @else
      <div class="coverage-grid coverage-full">
        @for(var group : view.coverage())
          <section class="coverage-group">
            <div class="coverage-head"><strong>${group.title()}</strong><span>${group.duration()}</span></div>
            @for(var item : group.items())
              <div class="coverage-row">
                <div class="coverage-copy">
                  <strong>${item.name()}</strong>
                  <small>${item.testCount()} scenarios · ${item.duration()}</small>
                </div>
                <div class="coverage-rate">
                  <strong>${item.successRate()}</strong>
                  <div class="bar-track"><span style="width:${item.passPercentage()}%;"></span></div>
                </div>
              </div>
            @endfor
          </section>
        @endfor
      </div>
    @endif
  </article>
</section>
