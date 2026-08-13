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
  var sort = document.querySelector('[data-report-sort]');
  var failedOnly = document.querySelector('[data-failed-only]');
  var clear = document.querySelector('[data-clear-filters]');
  var count = document.querySelector('[data-result-count]');

  function detailFor(row) {
    var next = row.nextElementSibling;
    return next && next.matches('[data-detail-row]') ? next : null;
  }

  function addOptions(select, values) {
    Array.from(new Set(values.filter(Boolean))).sort(function (a, b) {
      return a.localeCompare(b);
    }).forEach(function (value) {
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

  function sortRows() {
    var mode = sort.value;
    var statusOrder = {FAILED: 0, PENDING: 1, UNDEFINED: 2, SKIPPED: 3, PASSED: 4};
    rows.sort(function (a, b) {
      if (mode === 'duration-asc') return Number(a.dataset.duration) - Number(b.dataset.duration);
      if (mode === 'name-asc') return a.cells[1].textContent.trim().localeCompare(b.cells[1].textContent.trim());
      if (mode === 'status') return (statusOrder[a.dataset.status] || 9) - (statusOrder[b.dataset.status] || 9);
      return Number(b.dataset.duration) - Number(a.dataset.duration);
    });
  }

  function apply() {
    sortRows();
    var query = search.value.trim().toLowerCase();
    var visible = 0;

    rows.forEach(function (row) {
      var detail = detailFor(row);
      body.appendChild(row);
      if (detail) body.appendChild(detail);

      var searchable = (row.textContent + ' ' + (detail ? detail.textContent : '')).toLowerCase();
      var matches = (!query || searchable.indexOf(query) >= 0)
        && (!statusFilter.value || row.dataset.status === statusFilter.value)
        && (!featureFilter.value || row.dataset.feature === featureFilter.value)
        && (!suiteFilter.value || row.dataset.suite === suiteFilter.value)
        && (!tagFilter.value || (row.dataset.tags || '').split(/[\s,]+/).indexOf(tagFilter.value) >= 0)
        && (!failedOnly.checked || row.dataset.status === 'FAILED');

      row.hidden = !matches;
      if (!matches && detail) detail.hidden = true;
      if (matches) visible++;
    });

    count.innerHTML = '<strong>' + visible + '</strong> / ' + rows.length + ' visible';
  }

  [statusFilter, featureFilter, suiteFilter, tagFilter, sort, failedOnly].forEach(function (control) {
    control.addEventListener('change', apply);
  });
  search.addEventListener('input', apply);

  clear.addEventListener('click', function () {
    search.value = '';
    statusFilter.value = '';
    featureFilter.value = '';
    suiteFilter.value = '';
    tagFilter.value = '';
    sort.value = 'duration-desc';
    failedOnly.checked = false;
    rows.forEach(function (row) {
      var detail = detailFor(row);
      if (detail) detail.hidden = true;
      var button = row.querySelector('[data-detail-toggle]');
      if (button) button.textContent = 'View details';
    });
    apply();
    search.focus();
  });

  document.addEventListener('click', function (event) {
    var button = event.target.closest('[data-detail-toggle]');
    if (!button) return;
    var row = button.closest('[data-scenario-row]');
    var detail = detailFor(row);
    if (!detail) return;
    detail.hidden = !detail.hidden;
    button.textContent = detail.hidden ? 'View details' : 'Hide details';
  });

  apply();
})();
</script>
