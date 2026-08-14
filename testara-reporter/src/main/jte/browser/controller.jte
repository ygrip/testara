<script>
(function () {
  var rows = Array.from(document.querySelectorAll('[data-scenario-row]'));
  if (!rows.length) return;

  var body = document.querySelector('[data-scenario-body]');
  var search = document.querySelector('[data-report-search]');
  var statusFilter = document.querySelector('[data-status-filter]');
  var featureFilter = document.querySelector('[data-feature-filter]');
  var suiteFilter = document.querySelector('[data-suite-filter]');
  var tagFilter = document.querySelector('[data-tag-filter]');
  var clear = document.querySelector('[data-clear-filters]');
  var count = document.querySelector('[data-result-count]');
  var sortButtons = Array.from(document.querySelectorAll('[data-sort-column]'));
  var listView = document.querySelector('[data-scenario-list-view]');
  var detailView = document.querySelector('[data-scenario-detail-view]');
  var closeDetail = document.querySelector('[data-close-scenario]');
  var detailPanels = Array.from(document.querySelectorAll('[data-scenario-detail-panel]'));
  var sortColumn = 'duration';
  var sortDirection = 'desc';
  var activePanel = null;
  var activeRow = null;

  function panelFor(row) {
    var id = row.dataset.scenarioId;
    return detailPanels.find(function (panel) { return panel.dataset.scenarioDetailPanel === id; }) || null;
  }

  function addOptions(select, values) {
    Array.from(new Set(values.filter(Boolean))).sort(function (a, b) { return a.localeCompare(b); }).forEach(function (value) {
      var option = document.createElement('option');
      option.value = value;
      option.textContent = value;
      select.appendChild(option);
    });
  }

  addOptions(featureFilter, rows.map(function (row) { return row.dataset.feature; }));
  addOptions(suiteFilter, rows.map(function (row) { return row.dataset.suite; }));
  addOptions(tagFilter, rows.flatMap(function (row) {
    return (row.dataset.tags || '').split(/[\s,]+/).map(function (tag) { return tag.trim(); }).filter(Boolean);
  }));

  function compareRows(a, b) {
    var statusOrder = {FAILED: 0, PENDING: 1, UNDEFINED: 2, SKIPPED: 3, PASSED: 4};
    var result;
    if (sortColumn === 'duration') {
      result = Number(a.dataset.duration) - Number(b.dataset.duration);
    } else if (sortColumn === 'status') {
      result = (statusOrder[a.dataset.status] ?? 9) - (statusOrder[b.dataset.status] ?? 9);
    } else if (sortColumn === 'feature') {
      result = (a.dataset.feature || '').localeCompare(b.dataset.feature || '');
    } else {
      result = (a.dataset.name || '').localeCompare(b.dataset.name || '');
    }
    return sortDirection === 'asc' ? result : -result;
  }

  function updateSortHeaders() {
    sortButtons.forEach(function (button) {
      var active = button.dataset.sortColumn === sortColumn;
      var indicator = button.querySelector('[data-sort-indicator]');
      var header = button.closest('th');
      button.classList.toggle('active', active);
      button.dataset.sortDirection = active ? sortDirection : '';
      button.setAttribute('aria-pressed', active ? 'true' : 'false');
      if (indicator) indicator.textContent = active ? (sortDirection === 'asc' ? '↑' : '↓') : '';
      if (header) header.setAttribute('aria-sort', active ? (sortDirection === 'asc' ? 'ascending' : 'descending') : 'none');
    });
  }

  function detailText(row) {
    var panel = panelFor(row);
    return panel ? panel.textContent : '';
  }

  function apply() {
    rows.sort(compareRows);
    var query = search.value.trim().toLowerCase();
    var visible = 0;
    rows.forEach(function (row) {
      body.appendChild(row);
      var searchable = (row.textContent + ' ' + detailText(row)).toLowerCase();
      var matches = (!query || searchable.indexOf(query) >= 0)
        && (!statusFilter.value || row.dataset.status === statusFilter.value)
        && (!featureFilter.value || row.dataset.feature === featureFilter.value)
        && (!suiteFilter.value || row.dataset.suite === suiteFilter.value)
        && (!tagFilter.value || (row.dataset.tags || '').split(/[\s,]+/).indexOf(tagFilter.value) >= 0);
      row.hidden = !matches;
      if (matches) visible++;
    });
    count.innerHTML = '<strong>' + visible + '</strong> / ' + rows.length + ' visible';
    updateSortHeaders();
  }

  function openScenario(row) {
    var panel = panelFor(row);
    if (!panel || !detailView.hidden) return;

    activeRow = row;
    activePanel = panel;
    detailPanels.forEach(function (candidate) { candidate.hidden = candidate !== panel; });

    listView.hidden = true;
    detailView.hidden = false;

    window.requestAnimationFrame(function () {
      detailView.scrollIntoView({block: 'start', behavior: 'auto'});
      if (closeDetail) closeDetail.focus({preventScroll: true});
    });
  }

  function closeScenario() {
    if (detailView.hidden) return;

    var rowToRestore = activeRow;
    detailView.hidden = true;
    if (activePanel) activePanel.hidden = true;
    listView.hidden = false;
    activePanel = null;
    activeRow = null;

    window.requestAnimationFrame(function () {
      if (rowToRestore) rowToRestore.focus();
    });
  }

  [statusFilter, featureFilter, suiteFilter, tagFilter].forEach(function (control) { control.addEventListener('change', apply); });
  search.addEventListener('input', apply);

  sortButtons.forEach(function (button) {
    button.addEventListener('click', function () {
      var nextColumn = button.dataset.sortColumn;
      if (sortColumn === nextColumn) sortDirection = sortDirection === 'asc' ? 'desc' : 'asc';
      else {
        sortColumn = nextColumn;
        sortDirection = nextColumn === 'duration' ? 'desc' : 'asc';
      }
      apply();
    });
  });

  clear.addEventListener('click', function () {
    search.value = '';
    statusFilter.value = '';
    featureFilter.value = '';
    suiteFilter.value = '';
    tagFilter.value = '';
    apply();
    search.focus();
  });

  rows.forEach(function (row) {
    row.addEventListener('click', function (event) {
      if (event.target.closest('a,button,input,select,video,audio')) return;
      openScenario(row);
    });
    row.addEventListener('keydown', function (event) {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        openScenario(row);
      }
    });
  });

  if (closeDetail) closeDetail.addEventListener('click', closeScenario);
  document.addEventListener('keydown', function (event) { if (event.key === 'Escape' && !detailView.hidden) closeScenario(); });

  apply();
})();
</script>
