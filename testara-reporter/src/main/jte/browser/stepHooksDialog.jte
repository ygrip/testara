@import io.github.ygrip.testara.reporter.view.ReportView
@param ReportView view
@if(view.scenarios() != null && !view.scenarios().isEmpty())
<dialog class="report-dialog step-hooks-dialog" data-step-hooks-dialog aria-labelledby="step-hooks-dialog-title">
  <div class="dialog-shell">
    <header class="dialog-header">
      <div><div class="card-eyebrow">Cucumber lifecycle</div><h3 id="step-hooks-dialog-title">Step hooks</h3><p>Hooks recorded around the selected scenario step.</p></div>
      <button class="icon-button" type="button" data-close-step-hooks aria-label="Close step hooks"><svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 6l12 12"></path><path d="M18 6L6 18"></path></svg></button>
    </header>
    <div class="dialog-scroll">
      @for(var scenario : view.scenarios())
        @for(var step : scenario.steps())
          <section class="step-hooks-panel" data-step-hooks-panel="${scenario.id()}" data-step-line="${step.line()}" hidden>
            <div class="dialog-context">
              <strong>${step.keyword()} ${step.name()}</strong>
              <span>${scenario.name()} · ${scenario.feature()}@if(step.line() != null) · line ${step.line()}@endif</span>
            </div>
            @if((step.beforeHooks() == null || step.beforeHooks().isEmpty()) && (step.afterHooks() == null || step.afterHooks().isEmpty()))
              <div class="empty-state compact-empty">No Before Step or After Step hooks were recorded for this step.</div>
            @else
              <div class="step-hooks-groups">
                @template.browser.hooks("Before step", step.beforeHooks())
                @template.browser.hooks("After step", step.afterHooks())
              </div>
            @endif
          </section>
        @endfor
      @endfor
    </div>
  </div>
</dialog>
@endif
