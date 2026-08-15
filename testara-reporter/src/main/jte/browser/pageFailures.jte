@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<section class="report-view" data-report-page="failures" hidden>
  <article class="glass-card card-pad report-section-card" data-glass-card data-full-row="failure-signals">
    <div class="card-head"><div><div class="card-eyebrow danger-eyebrow">Reliability</div><h2 class="card-title">Failure signals</h2><p class="card-subtitle">Recurring failures and unstable features, ranked so the largest reliability risks are obvious first.</p></div><span class="icon-tile danger">!</span></div>
    <div class="report-section-scroll failure-scroll" tabindex="0">
      @if(view.frequentFailures().isEmpty())
        <div class="empty-state">No recurring failure signatures were detected.</div>
      @else
        <div class="rank-list wide-rank-list">
          @for(var failure : view.frequentFailures())
            <div class="rank-item signal-row"><div class="rank-head"><span>${failure.name()}</span><strong class="danger-value">${failure.count()}×</strong></div><div class="bar-track danger"><span style="width:${failure.relativePercentage()}%;"></span></div><small>${failure.error()}</small></div>
          @endfor
        </div>
      @endif
      @if(!view.unstableFeatures().isEmpty())
        <div class="subsection-block"><div class="card-eyebrow warning-eyebrow">Unstable features</div><div class="rank-list wide-rank-list subsection-list">
          @for(var feature : view.unstableFeatures())
            <div class="rank-item"><div class="rank-head"><span>${feature.name()}</span><strong>${feature.failurePercentage()}% failed</strong></div><div class="bar-track danger"><span style="width:${feature.failurePercentage()}%;"></span></div></div>
          @endfor
        </div></div>
      @endif
    </div>
  </article>
</section>
