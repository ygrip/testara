<dialog class="report-dialog media-viewer" data-media-viewer aria-labelledby="media-viewer-title">
  <div class="media-dialog-shell">
    <header class="dialog-header media-dialog-header">
      <div><div class="card-eyebrow">Attachment</div><h3 id="media-viewer-title" data-media-title>Attachment preview</h3></div>
      <button class="icon-button" type="button" data-media-close aria-label="Close attachment viewer"><svg viewBox="0 0 24 24" width="19" height="19" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 6l12 12"></path><path d="M18 6L6 18"></path></svg></button>
    </header>

    <section class="image-viewer" data-image-viewer hidden>
      <div class="media-toolbar" aria-label="Image controls">
        <button class="media-tool" type="button" data-image-prev aria-label="Previous image" title="Previous image"><svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2"><path d="m15 18-6-6 6-6"></path></svg></button>
        <button class="media-tool" type="button" data-image-next aria-label="Next image" title="Next image"><svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2"><path d="m9 18 6-6-6-6"></path></svg></button>
        <span class="media-tool-divider"></span>
        <button class="media-tool" type="button" data-image-zoom-out aria-label="Zoom out" title="Zoom out">−</button>
        <button class="media-tool" type="button" data-image-zoom-in aria-label="Zoom in" title="Zoom in">+</button>
        <button class="media-tool text-tool" type="button" data-image-reset aria-label="Reset zoom">100%</button>
        <button class="media-tool text-tool" type="button" data-image-fit>Fit</button>
      </div>
      <div class="image-stage" data-image-stage><img data-media-image alt=""></div>
    </section>

    <section class="video-viewer" data-video-viewer hidden>
      <div class="video-stage"><video data-media-video controls playsinline preload="metadata"></video></div>
      <div class="media-toolbar video-toolbar"><button class="media-tool text-tool" type="button" data-video-fullscreen>Fullscreen</button></div>
    </section>
  </div>
</dialog>
