@import io.github.ygrip.testara.reporter.view.ReportView.ReportScenario
@param ReportScenario scenario
@if(scenario.beforeHooks() != null && !scenario.beforeHooks().isEmpty())
<details class="scenario-hooks-disclosure" data-scenario-hooks="before">
  <summary class="scenario-hooks-summary">
    <span class="scenario-hooks-copy"><strong>Before scenario</strong><small>${scenario.beforeHooks().size()} recorded hooks</small></span>
    <svg class="disclosure-chevron" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6"></path></svg>
  </summary>
  <div class="scenario-hooks-body">
    @template.browser.hooks("Before scenario", scenario.beforeHooks())
  </div>
</details>
@endif
@if(scenario.afterHooks() != null && !scenario.afterHooks().isEmpty())
<details class="scenario-hooks-disclosure" data-scenario-hooks="after">
  <summary class="scenario-hooks-summary">
    <span class="scenario-hooks-copy"><strong>After scenario</strong><small>${scenario.afterHooks().size()} recorded hooks</small></span>
    <svg class="disclosure-chevron" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6"></path></svg>
  </summary>
  <div class="scenario-hooks-body">
    @template.browser.hooks("After scenario", scenario.afterHooks())
  </div>
</details>
@endif
