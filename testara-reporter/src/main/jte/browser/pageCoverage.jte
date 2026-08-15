@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<section class="report-view" data-report-page="coverage" hidden>
  <article class="glass-card card-pad report-section-card" data-glass-card data-full-row="functional-coverage">
    <div class="card-head"><div><div class="card-eyebrow">Coverage</div><h2 class="card-title">Functional coverage</h2><p class="card-subtitle">Feature pass rates and execution density grouped by the suites Testara inferred from your Cucumber structure.</p></div><span class="icon-tile">▦</span></div>
    @if(view.coverage().isEmpty())
      <div class="empty-state">No feature coverage data is available.</div>
    @else
      <div class="coverage-scroll" data-coverage-scroll tabindex="0" aria-label="Scrollable functional coverage"><div class="coverage-grid coverage-full">
        @for(var group : view.coverage())
          <section class="coverage-group"><div class="coverage-head"><strong>${group.title()}</strong><span>${group.duration()}</span></div>
            @for(var item : group.items())
              <div class="coverage-row"><div class="coverage-copy"><strong>${item.name()}</strong><small>${item.testCount()} scenarios · ${item.duration()}</small></div><div class="coverage-rate"><strong>${item.successRate()}</strong><div class="bar-track"><span style="width:${item.passPercentage()}%;"></span></div></div></div>
            @endfor
          </section>
        @endfor
      </div></div>
    @endif
  </article>
</section>
