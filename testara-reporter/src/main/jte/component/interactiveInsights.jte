@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<section class="insight-layout">
  <article class="surface-card insight-card">
    <div class="section-heading">
      <div><span class="eyebrow dark">Reliability</span><h2>Failure signals</h2></div>
      <span class="section-icon danger">!</span>
    </div>
    @if(view.frequentFailures().isEmpty())
      <div class="healthy-state"><span>✓</span><div><strong>No recurring failures</strong><small>No repeated failure signature was detected in this run.</small></div></div>
    @else
      <div class="rank-list">
        @for(var failure : view.frequentFailures())
          <div class="rank-item">
            <div class="rank-head"><span title="${failure.error()}">${failure.name()}</span><strong>${failure.count()}×</strong></div>
            <div class="bar-track danger"><span style="width:${failure.relativePercentage()}%"></span></div>
            <small>${failure.error()}</small>
          </div>
        @endfor
      </div>
    @endif
    @if(!view.unstableFeatures().isEmpty())
      <h3 class="subheading">Most unstable features</h3>
      <div class="rank-list compact-list">
        @for(var feature : view.unstableFeatures())
          <div class="rank-item">
            <div class="rank-head"><span>${feature.name()}</span><strong class="danger-text">${feature.failurePercentage()}%</strong></div>
            <div class="bar-track danger"><span style="width:${feature.failurePercentage()}%"></span></div>
          </div>
        @endfor
      </div>
    @endif
  </article>

  <article class="surface-card insight-card">
    <div class="section-heading">
      <div><span class="eyebrow dark">Performance</span><h2>Slowest scenarios</h2></div>
      <span class="section-icon info">◷</span>
    </div>
    @if(view.longestScenarios().isEmpty())
      <div class="empty-compact">No scenario duration data is available.</div>
    @else
      <div class="rank-list">
        @for(var item : view.longestScenarios())
          <div class="rank-item">
            <div class="rank-head"><span>${item.label()}</span><strong>${item.duration()}</strong></div>
            <div class="bar-track"><span style="width:${item.percentage()}%"></span></div>
          </div>
        @endfor
      </div>
    @endif
    @if(!view.longestSteps().isEmpty())
      <h3 class="subheading">Slowest steps</h3>
      <div class="rank-list compact-list">
        @for(var item : view.longestSteps())
          <div class="rank-item">
            <div class="rank-head"><span>${item.label()}@if(item.count() != null) <small>· ${item.count()} calls</small>@endif</span><strong>${item.duration()}</strong></div>
            <div class="bar-track info"><span style="width:${item.percentage()}%"></span></div>
          </div>
        @endfor
      </div>
    @endif
  </article>
</section>

<section class="surface-card coverage-browser-card">
  <div class="section-heading">
    <div><span class="eyebrow dark">Functional signal</span><h2>Coverage by feature</h2></div>
    <span class="section-icon">▦</span>
  </div>
  @if(view.coverage().isEmpty())
    <div class="empty-compact">No feature coverage data is available.</div>
  @else
    <div class="coverage-browser-grid">
      @for(var group : view.coverage())
        <div class="coverage-group">
          <div class="coverage-group-head"><strong>${group.title()}</strong><span>${group.duration()}</span></div>
          @for(var item : group.items())
            <div class="coverage-browser-row">
              <div class="coverage-copy"><span>${item.name()}</span><small>${item.testCount()} tests · ${item.duration()}</small></div>
              <div class="coverage-rate"><strong>${item.successRate()}</strong><div class="bar-track"><span style="width:${item.passPercentage()}%"></span></div></div>
            </div>
          @endfor
        </div>
      @endfor
    </div>
  @endif
</section>
