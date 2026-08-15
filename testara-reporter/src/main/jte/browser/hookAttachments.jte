@import java.util.List
@import io.github.ygrip.testara.reporter.view.ReportView.ReportHook
@param List<ReportHook> hooks
@if(hooks != null && !hooks.isEmpty())
  @for(var hook : hooks)
    @if(hook.attachments() != null && !hook.attachments().isEmpty())
      <div class="step-artifact hook-attachment-artifact"><div class="artifact-label">Hook attachments</div>@template.browser.attachments(hook.attachments())</div>
    @endif
  @endfor
@endif
