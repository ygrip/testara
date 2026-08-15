@import java.util.List
@import io.github.ygrip.testara.reporter.view.ReportView.ReportAttachment
@param List<ReportAttachment> attachments
@if(attachments != null && !attachments.isEmpty())
<div class="attachment-grid">
  @for(var attachment : attachments)
    @if(attachment.kind().equals("image"))
      <button class="attachment-card attachment-button attachment-visual-card" type="button" data-open-image data-attachment-name="${attachment.name()}" data-attachment-src="${attachment.uri()}" aria-label="Open image attachment ${attachment.name()}">
        <span class="attachment-visual-stage"><img class="attachment-image" src="${attachment.uri()}" alt="${attachment.name()}" loading="lazy"></span>
        <span class="attachment-caption"><span>${attachment.name()}</span><small>Open image</small></span>
      </button>
    @elseif(attachment.kind().equals("video"))
      <button class="attachment-card attachment-button attachment-visual-card" type="button" data-open-video data-attachment-name="${attachment.name()}" data-attachment-src="${attachment.uri()}" data-attachment-mime="${attachment.mimeType()}" aria-label="Open video attachment ${attachment.name()}">
        <span class="attachment-visual-stage"><span class="video-attachment-preview"><span class="video-play-icon" aria-hidden="true">▶</span></span></span>
        <span class="attachment-caption"><span>${attachment.name()}</span><small>Open video</small></span>
      </button>
    @elseif(attachment.kind().equals("audio"))
      <div class="attachment-card attachment-audio"><audio controls preload="metadata"><source src="${attachment.uri()}" type="${attachment.mimeType()}"></audio><span>${attachment.name()}</span></div>
    @else
      <a class="attachment-file" href="${attachment.uri()}" download="${attachment.name()}">${attachment.name()} · ${attachment.mimeType()}</a>
    @endif
  @endfor
</div>
@endif
