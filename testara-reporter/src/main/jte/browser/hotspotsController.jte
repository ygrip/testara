<script>
(function () {
  var tabs = Array.from(document.querySelectorAll('[data-hotspot-tab]'));
  var panels = Array.from(document.querySelectorAll('[data-hotspot-panel]'));
  if (!tabs.length) return;

  function activate(tab, focus) {
    var key = tab.dataset.hotspotTab;
    tabs.forEach(function (candidate) {
      var active = candidate === tab;
      candidate.classList.toggle('active', active);
      candidate.setAttribute('aria-selected', active ? 'true' : 'false');
      candidate.tabIndex = active ? 0 : -1;
    });
    panels.forEach(function (panel) { panel.hidden = panel.dataset.hotspotPanel !== key; });
    if (focus) tab.focus();
  }

  tabs.forEach(function (tab, index) {
    tab.addEventListener('click', function () { activate(tab, false); });
    tab.addEventListener('keydown', function (event) {
      if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
      event.preventDefault();
      var delta = event.key === 'ArrowRight' ? 1 : -1;
      activate(tabs[(index + delta + tabs.length) % tabs.length], true);
    });
  });
})();
</script>
