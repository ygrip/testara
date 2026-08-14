@import java.util.List
@import io.github.ygrip.testara.reporter.view.ReportView.ReportHook
@param String label
@param List<ReportHook> hooks
@if(hooks != null && !hooks.isEmpty())
<div class="hook-section">
  @for(var hook : hooks)
    <section class="hook-card">
      <div class="hook-head">
        <div class="hook-title"><span class="status-pill status-${hook.status().toLowerCase()}">${hook.status()}</span><strong>${label}</strong></div>
        <small>${hook.duration()}</small>
      </div>
      @if(hook.location() != null && !hook.location().isBlank())<div class="hook-location">${hook.location()}</div>@endif
      @if(hook.arguments() != null && !hook.arguments().isEmpty())
        <div class="hook-arguments">@for(var argument : hook.arguments())<span>${argument.value()}@if(argument.offset() != null) · offset ${argument.offset()}@endif</span>@endfor</div>
      @endif
      @if(hook.outputs() != null && !hook.outputs().isEmpty())
        <div class="step-artifact"><div class="artifact-label">Output</div><div class="output-list">@for(var output : hook.outputs())<div>${output}</div>@endfor</div></div>
      @endif
      @if(hook.attachments() != null && !hook.attachments().isEmpty())
        <div class="step-artifact"><div class="artifact-label">Attachments</div>@template.browser.attachments(hook.attachments())</div>
      @endif
      @template.browser.error("Hook error", hook.error())
    </section>
  @endfor
</div>
@endif
