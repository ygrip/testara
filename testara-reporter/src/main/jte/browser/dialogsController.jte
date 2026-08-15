<script>
(function () {
  function focusable(dialog) {
    return Array.from(dialog.querySelectorAll('button:not([disabled]),a[href],input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])')).filter(function (item) { return !item.hidden; });
  }

  function trapFocus(dialog, event) {
    if (event.key !== 'Tab') return;
    var items = focusable(dialog);
    if (!items.length) return;
    var first = items[0];
    var last = items[items.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  }

  var hookDialog = document.querySelector('[data-step-hooks-dialog]');
  var hookPanels = hookDialog ? Array.from(hookDialog.querySelectorAll('[data-step-hooks-panel]')) : [];
  var hookTrigger = null;

  function hookPanelFor(id, line) {
    return hookPanels.find(function (panel) {
      return panel.dataset.stepHooksPanel === id && panel.dataset.stepLine === line;
    }) || null;
  }

  document.querySelectorAll('[data-open-step-hooks]').forEach(function (button) {
    button.addEventListener('click', function () {
      if (!hookDialog) return;
      var panel = hookPanelFor(button.dataset.scenarioId, button.dataset.stepLine);
      if (!panel) return;
      hookPanels.forEach(function (candidate) { candidate.hidden = candidate !== panel; });
      hookTrigger = button;
      hookDialog.showModal();
      var close = hookDialog.querySelector('[data-close-step-hooks]');
      if (close) close.focus();
    });
  });

  if (hookDialog) {
    var closeHook = hookDialog.querySelector('[data-close-step-hooks]');
    if (closeHook) closeHook.addEventListener('click', function () { hookDialog.close(); });
    hookDialog.addEventListener('click', function (event) { if (event.target === hookDialog) hookDialog.close(); });
    hookDialog.addEventListener('keydown', function (event) { trapFocus(hookDialog, event); });
    hookDialog.addEventListener('close', function () {
      hookPanels.forEach(function (panel) { panel.hidden = true; });
      if (hookTrigger) hookTrigger.focus();
      hookTrigger = null;
    });
  }

  var mediaDialog = document.querySelector('[data-media-viewer]');
  if (!mediaDialog) return;
  var title = mediaDialog.querySelector('[data-media-title]');
  var imageView = mediaDialog.querySelector('[data-image-viewer]');
  var videoView = mediaDialog.querySelector('[data-video-viewer]');
  var image = mediaDialog.querySelector('[data-media-image]');
  var video = mediaDialog.querySelector('[data-media-video]');
  var prev = mediaDialog.querySelector('[data-image-prev]');
  var next = mediaDialog.querySelector('[data-image-next]');
  var zoomIn = mediaDialog.querySelector('[data-image-zoom-in]');
  var zoomOut = mediaDialog.querySelector('[data-image-zoom-out]');
  var reset = mediaDialog.querySelector('[data-image-reset]');
  var fit = mediaDialog.querySelector('[data-image-fit]');
  var fullscreen = mediaDialog.querySelector('[data-video-fullscreen]');
  var mediaTrigger = null;
  var images = [];
  var imageIndex = 0;
  var scale = 1;

  function scenarioIdFor(trigger) {
    var scenario = trigger.closest('[data-scenario-detail-panel]');
    if (scenario) return scenario.dataset.scenarioDetailPanel;
    var hookPanel = trigger.closest('[data-step-hooks-panel]');
    return hookPanel ? hookPanel.dataset.stepHooksPanel : null;
  }

  function scenarioRoots(id) {
    if (!id) return [];
    return Array.from(document.querySelectorAll('[data-scenario-detail-panel="' + id + '"],[data-step-hooks-panel="' + id + '"]'));
  }

  function imageSource(trigger) {
    var preview = trigger.querySelector('img');
    return preview ? (preview.getAttribute('src') || '') : '';
  }

  function scenarioImages(trigger) {
    var roots = scenarioRoots(scenarioIdFor(trigger));
    var candidates = roots.length ? roots.flatMap(function (root) { return Array.from(root.querySelectorAll('[data-open-image]')); }) : [trigger];
    var seen = new Set();
    return candidates.filter(function (candidate) {
      var key = imageSource(candidate) + '\n' + candidate.dataset.attachmentName;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }

  function updateZoomLabel() { if (reset) reset.textContent = Math.round(scale * 100) + '%'; }
  function resetImage(natural) {
    scale = 1;
    image.classList.toggle('fit', !natural);
    image.style.transform = natural ? 'scale(1)' : 'none';
    updateZoomLabel();
  }
  function setScale(nextScale) {
    scale = Math.max(0.25, Math.min(4, nextScale));
    image.classList.remove('fit');
    image.style.transform = 'scale(' + scale + ')';
    updateZoomLabel();
  }
  function renderImage() {
    var trigger = images[imageIndex];
    if (!trigger) return;
    var name = trigger.dataset.attachmentName || 'Image attachment';
    title.textContent = name;
    image.src = imageSource(trigger);
    image.alt = name;
    if (prev) prev.disabled = images.length < 2;
    if (next) next.disabled = images.length < 2;
    resetImage(false);
  }
  function prepareMediaDialog(trigger) { mediaTrigger = trigger; }
  function openImage(trigger) {
    prepareMediaDialog(trigger);
    images = scenarioImages(trigger);
    imageIndex = Math.max(0, images.findIndex(function (candidate) {
      return imageSource(candidate) === imageSource(trigger) && candidate.dataset.attachmentName === trigger.dataset.attachmentName;
    }));
    videoView.hidden = true;
    imageView.hidden = false;
    renderImage();
    mediaDialog.showModal();
  }
  function openVideo(trigger) {
    prepareMediaDialog(trigger);
    imageView.hidden = true;
    videoView.hidden = false;
    title.textContent = trigger.dataset.attachmentName || 'Video attachment';
    video.src = trigger.dataset.attachmentSrc || '';
    mediaDialog.showModal();
  }

  document.querySelectorAll('[data-open-image]').forEach(function (button) { button.addEventListener('click', function () { openImage(button); }); });
  document.querySelectorAll('[data-open-video]').forEach(function (button) { button.addEventListener('click', function () { openVideo(button); }); });
  mediaDialog.querySelectorAll('[data-media-close]').forEach(function (button) { button.addEventListener('click', function () { mediaDialog.close(); }); });
  mediaDialog.addEventListener('click', function (event) { if (event.target === mediaDialog) mediaDialog.close(); });
  mediaDialog.addEventListener('keydown', function (event) { trapFocus(mediaDialog, event); });
  mediaDialog.addEventListener('close', function () {
    if (video) { video.pause(); video.removeAttribute('src'); video.load(); }
    image.removeAttribute('src');
    imageView.hidden = true;
    videoView.hidden = true;
    images = [];
    if (mediaTrigger) mediaTrigger.focus();
    mediaTrigger = null;
  });

  if (prev) prev.addEventListener('click', function () { if (images.length) { imageIndex = (imageIndex - 1 + images.length) % images.length; renderImage(); } });
  if (next) next.addEventListener('click', function () { if (images.length) { imageIndex = (imageIndex + 1) % images.length; renderImage(); } });
  if (zoomIn) zoomIn.addEventListener('click', function () { setScale(scale + 0.25); });
  if (zoomOut) zoomOut.addEventListener('click', function () { setScale(scale - 0.25); });
  if (reset) reset.addEventListener('click', function () { resetImage(true); });
  if (fit) fit.addEventListener('click', function () { resetImage(false); });
  if (fullscreen) fullscreen.addEventListener('click', function () { if (video && video.requestFullscreen) video.requestFullscreen(); });

  document.addEventListener('keydown', function (event) {
    if (!mediaDialog.open || imageView.hidden) return;
    if (event.key === 'ArrowLeft' && images.length > 1) { event.preventDefault(); prev.click(); }
    if (event.key === 'ArrowRight' && images.length > 1) { event.preventDefault(); next.click(); }
  });
})();
</script>
