@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<section class="report-view" data-report-page="hotspots" hidden>
  <article class="glass-card card-pad report-section-card" data-glass-card data-full-row="execution-hotspots">
    <div class="card-head"><div><div class="card-eyebrow">Performance</div><h2 class="card-title">Execution hotspots</h2><p class="card-subtitle">Switch between the slowest scenarios and recurring slow step families without stacking both lists into one long page.</p></div><span class="icon-tile info">↗</span></div>
    <div class="hotspot-tabs" role="tablist" aria-label="Execution hotspot views">
      <button class="hotspot-tab active" type="button" role="tab" aria-selected="true" aria-controls="hotspot-scenarios" data-hotspot-tab="scenarios">Slowest scenarios</button>
      <button class="hotspot-tab" type="button" role="tab" aria-selected="false" tabindex="-1" aria-controls="hotspot-steps" data-hotspot-tab="steps">Slowest steps</button>
    </div>
    <section id="hotspot-scenarios" class="hotspot-panel report-section-scroll" role="tabpanel" data-hotspot-panel="scenarios">
      @if(view.longestScenarios().isEmpty())
        <div class="empty-state">No scenario duration data is available.</div>
      @else
        <div class="rank-list wide-rank-list">@for(var item : view.longestScenarios())<div class="rank-item"><div class="rank-head"><span>${item.label()}</span><strong>${item.duration()}</strong></div><div class="bar-track"><span style="width:${item.percentage()}%;"></span></div></div>@endfor</div>
      @endif
    </section>
    <section id="hotspot-steps" class="hotspot-panel report-section-scroll" role="tabpanel" data-hotspot-panel="steps" hidden>
      @if(view.longestSteps().isEmpty())
        <div class="empty-state">No step duration data is available.</div>
      @else
        <div class="rank-list wide-rank-list">@for(var item : view.longestSteps())<div class="rank-item"><div class="rank-head"><span>${item.label()}</span><strong>${item.duration()}</strong></div><div class="bar-track"><span style="width:${item.percentage()}%;"></span></div>@if(item.count() != null)<small>${item.count()} executions</small>@endif</div>@endfor</div>
      @endif
    </section>
  </article>
</section>
