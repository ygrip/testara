@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
<section class="report-view" data-report-page="additional-info" hidden>
  <article class="glass-card card-pad report-section-card" data-glass-card>
    <div class="card-head compact-head"><div><div class="card-eyebrow">Run context</div><h2 class="card-title">Additional information</h2><p class="card-subtitle">Execution metadata supplied by Testara and your custom fields.</p></div><span class="icon-tile info">i</span></div>
    @if(view.customFields().isEmpty())
      <div class="empty-state">No additional report fields were supplied.</div>
    @else
      <div class="context-table-shell additional-info-scroll" data-additional-info-scroll tabindex="0" aria-label="Scrollable additional report information">
        <table class="context-table" data-run-context-table><tbody>
          @for(var field : view.customFields())
            <tr><th scope="row">${field.label()}</th><td>${field.value()}</td></tr>
          @endfor
        </tbody></table>
      </div>
    @endif
  </article>
</section>
