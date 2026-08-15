<script>
(function () {
  'use strict';

  var rows = Array.prototype.slice.call(document.querySelectorAll('[data-scenario-row]'));
  var body = document.querySelector('[data-scenario-body]');
  var search = document.querySelector('[data-report-search]');
  var statusFilter = document.querySelector('[data-status-filter]');
  var suiteFilter = document.querySelector('[data-suite-filter]');
  var featureFilter = document.querySelector('[data-feature-filter]');
  var tagFilter = document.querySelector('[data-tag-filter]');
  var sort = document.querySelector('[data-report-sort]');
  var failedOnly = document.querySelector('[data-failed-only]');
  var clear = document.querySelector('[data-clear-filters]');
  var visibleCount = document.querySelector('[data-visible-count]');
  var empty = document.querySelector('[data-empty-results]');

  function unique(values) {
    return values.filter(function (value, index, all) {
      return value && all.indexOf(value) === index;
    }).sort(function (a, b) { return a.localeCompare(b); });
  }

  function fillOptions(select, values) {
    if (!select) return;
    unique(values).forEach(function (value) {
      var option = document.createElement('option');
      option.value = value;
      option.textContent = value;
      select.appendChild(option);
    });
  }

  fillOptions(statusFilter, rows.map(function (row) { return row.dataset.status; }));
  fillOptions(suiteFilter, rows.map(function (row) { return row.dataset.suite; }));
  fillOptions(featureFilter, rows.map(function (row) { return row.dataset.feature; }));
  fillOptions(tagFilter, rows.reduce(function (result, row) {
    String(row.dataset.tags || '').split(',').forEach(function (tag) {
      tag = tag.trim();
      if (tag) result.push(tag);
    });
    return result;
  }, []));

  function detailFor(row) {
    return document.querySelector('[data-detail-for="' + row.dataset.id + '"]');
  }

  function searchableText(row) {
    var detail = detailFor(row);
    return ((row.textContent || '') + ' ' + (detail ? detail.textContent || '' : '')).toLowerCase();
  }

  function matches(row) {
    var query = search ? search.value.trim().toLowerCase() : '';
    var status = statusFilter ? statusFilter.value : '';
    var suite = suiteFilter ? suiteFilter.value : '';
    var feature = featureFilter ? featureFilter.value : '';
    var tag = tagFilter ? tagFilter.value : '';
    var rowTags = String(row.dataset.tags || '').split(',').map(function (value) { return value.trim(); });

    if (query && searchableText(row).indexOf(query) === -1) return false;
    if (status && row.dataset.status !== status) return false;
    if (suite && row.dataset.suite !== suite) return false;
    if (feature && row.dataset.feature !== feature) return false;
    if (tag && rowTags.indexOf(tag) === -1) return false;
    if (failedOnly && failedOnly.checked && row.dataset.status === 'PASSED') return false;
    return true;
  }

  function compareRows(a, b) {
    var mode = sort ? sort.value : 'duration-desc';
    if (mode === 'duration-asc') return Number(a.dataset.duration) - Number(b.dataset.duration);
    if (mode === 'name-asc') return a.cells[1].textContent.trim().localeCompare(b.cells[1].textContent.trim());
    if (mode === 'status-asc') return a.dataset.status.localeCompare(b.dataset.status);
    return Number(b.dataset.duration) - Number(a.dataset.duration);
  }

  function apply() {
    if (!body) return;
    rows.sort(compareRows).forEach(function (row) {
      var detail = detailFor(row);
      body.appendChild(row);
      if (detail) body.appendChild(detail);
    });

    var count = 0;
    rows.forEach(function (row) {
      var show = matches(row);
      row.hidden = !show;
      var detail = detailFor(row);
      if (detail && !show) detail.hidden = true;
      if (!show) {
        var button = row.querySelector('[data-detail-toggle]');
        if (button) button.setAttribute('aria-expanded', 'false');
      } else {
        count++;
      }
    });

    if (visibleCount) visibleCount.textContent = String(count);
    if (empty) empty.hidden = count !== 0;
  }

  [search, statusFilter, suiteFilter, featureFilter, tagFilter, sort, failedOnly].forEach(function (control) {
    if (!control) return;
    control.addEventListener(control === search ? 'input' : 'change', apply);
  });

  if (clear) {
    clear.addEventListener('click', function () {
      if (search) search.value = '';
      [statusFilter, suiteFilter, featureFilter, tagFilter].forEach(function (control) {
        if (control) control.value = '';
      });
      if (sort) sort.value = 'duration-desc';
      if (failedOnly) failedOnly.checked = false;
      apply();
    });
  }

  document.addEventListener('click', function (event) {
    var button = event.target.closest('[data-detail-toggle]');
    if (!button) return;
    var detail = document.querySelector('[data-detail-for="' + button.getAttribute('data-detail-toggle') + '"]');
    if (!detail) return;
    var expanded = button.getAttribute('aria-expanded') === 'true';
    detail.hidden = expanded;
    button.setAttribute('aria-expanded', String(!expanded));
    button.textContent = expanded ? 'View' : 'Hide';
  });

  var donut = document.querySelector('[data-status-donut]');
  if (donut && rows.length) {
    var colors = {
      PASSED: '#22C55E',
      FAILED: '#EF4444',
      SKIPPED: '#64748B',
      PENDING: '#F59E0B',
      UNDEFINED: '#F97316'
    };
    var counts = rows.reduce(function (result, row) {
      var status = row.dataset.status || 'UNDEFINED';
      result[status] = (result[status] || 0) + 1;
      return result;
    }, {});
    var offset = 0;
    var segments = [];
    Object.keys(colors).forEach(function (status) {
      var count = counts[status] || 0;
      if (!count) return;
      var next = offset + (count / rows.length * 100);
      segments.push(colors[status] + ' ' + offset.toFixed(2) + '% ' + next.toFixed(2) + '%');
      offset = next;
    });
    donut.style.background = segments.length ? 'conic-gradient(' + segments.join(',') + ')' : '#E2E8F0';
  }

  apply();
}());
</script>
