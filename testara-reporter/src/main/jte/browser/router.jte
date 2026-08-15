<script>
(function () {
  var allowed = ['overview', 'additional-info', 'failures', 'hotspots', 'coverage', 'scenarios'];
  var pages = Array.from(document.querySelectorAll('[data-report-page]'));
  var links = Array.from(document.querySelectorAll('[data-report-route]'));
  var current = null;

  function normalizeHash() {
    var route = window.location.hash.replace(/^#/, '');
    return allowed.indexOf(route) >= 0 ? route : 'overview';
  }

  function renderRoute() {
    var route = normalizeHash();
    var previous = current;
    if (window.location.hash !== '#' + route) {
      window.history.replaceState(null, '', '#' + route);
    }
    pages.forEach(function (page) { page.hidden = page.dataset.reportPage !== route; });
    links.forEach(function (link) {
      var active = link.dataset.reportRoute === route;
      link.classList.toggle('active', active);
      if (active) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    });
    current = route;
    document.dispatchEvent(new CustomEvent('testara:routechange', {detail: {from: previous, to: route}}));
    window.scrollTo({top: 0, behavior: 'auto'});
  }

  window.addEventListener('hashchange', renderRoute);
  renderRoute();
})();
</script>
