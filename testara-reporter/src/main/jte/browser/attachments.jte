@import java.util.List
@import io.github.ygrip.testara.reporter.view.ReportView.ReportAttachment
@param List<ReportAttachment> attachments
@if(attachments != null && !attachments.isEmpty())
<div class="attachment-grid">
  @for(var attachment : attachments)
    @if(attachment.kind().equals("image"))
      <figure class="attachment-card"><img class="attachment-image" src="${attachment.uri()}" alt="${attachment.name()}" loading="lazy"><figcaption>${attachment.name()}</figcaption></figure>
    @elseif(attachment.kind().equals("video"))
      <figure class="attachment-card"><video class="attachment-video" controls preload="metadata"><source src="${attachment.uri()}" type="${attachment.mimeType()}"></video><figcaption>${attachment.name()}</figcaption></figure>
    @elseif(attachment.kind().equals("audio"))
      <div class="attachment-card attachment-audio"><audio controls preload="metadata"><source src="${attachment.uri()}" type="${attachment.mimeType()}"></audio><span>${attachment.name()}</span></div>
    @else
      <a class="attachment-file" href="${attachment.uri()}" download="${attachment.name()}">${attachment.name()} · ${attachment.mimeType()}</a>
    @endif
  @endfor
</div>
@endif
