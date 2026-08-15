<style>
  :root{--violet:#8B5CF6;--violet-soft:#F3EEFF;--cyan:#38BDF8;--cyan-soft:#ECF8FF;--theme-glass:rgba(255,255,255,.86);--nav-glass:rgba(255,255,255,.74);--attachment-stage:#E8F4F5;--attachment-stage-line:#B8DCDD}
  body{background:radial-gradient(circle at 7% 0,rgba(0,175,165,.14),transparent 27%),radial-gradient(circle at 94% 2%,rgba(139,92,246,.12),transparent 25%),linear-gradient(rgba(15,23,42,.022) 1px,transparent 1px),linear-gradient(90deg,rgba(15,23,42,.022) 1px,transparent 1px),linear-gradient(180deg,#F1FAFA,#F7F8FC 360px,#F5F8FB);background-size:auto,auto,28px 28px,28px 28px,auto}
  .glass-card{background:var(--theme-glass);box-shadow:0 10px 30px rgba(22,44,64,.07),inset 0 1px 0 rgba(255,255,255,.72)}
  .glass-card:before{background:linear-gradient(90deg,var(--teal),var(--cyan) 48%,rgba(139,92,246,.7) 74%,transparent 94%)}
  .hero{background:radial-gradient(circle at 92% 2%,rgba(139,92,246,.24),transparent 31%),radial-gradient(circle at 70% 118%,rgba(0,175,165,.25),transparent 38%),linear-gradient(118deg,#091727,#10283A 58%,#173247)}
  .hero:before{background:linear-gradient(180deg,var(--teal),var(--cyan) 64%,var(--violet))}
  .hero-chip{border-radius:999px;background:rgba(255,255,255,.07);box-shadow:inset 0 1px 0 rgba(255,255,255,.06)}
  .metric-card.accent-teal:before{background:linear-gradient(90deg,var(--teal),var(--mint),transparent 84%)}
  .metric-card.accent-cyan:before{background:linear-gradient(90deg,var(--cyan),var(--teal),transparent 84%)}
  .metric-card.accent-violet:before{background:linear-gradient(90deg,var(--violet),var(--cyan),transparent 84%)}
  .metric-card.accent-cyan .icon-tile{color:#0369A1;background:var(--cyan-soft);border-color:#BAE6FD}
  .metric-card.accent-violet .icon-tile{color:#6D28D9;background:var(--violet-soft);border-color:#DDD6FE}
  .status-chart-card{background:linear-gradient(140deg,rgba(255,255,255,.94),rgba(243,250,251,.84) 72%,rgba(243,238,255,.5))}
  .control-dock{background:linear-gradient(145deg,rgba(255,255,255,.96),rgba(247,252,252,.92))}
  .mobile-filter-toggle,.mobile-sort-strip{display:none}

  .report-navigation{position:sticky;top:10px;z-index:30;display:flex;align-items:center;gap:10px;margin-bottom:14px;padding:8px;border:1px solid rgba(184,205,215,.72);border-radius:16px;background:linear-gradient(135deg,var(--nav-glass),rgba(244,252,252,.68));box-shadow:0 14px 34px rgba(31,60,78,.11),inset 0 1px 0 rgba(255,255,255,.74);-webkit-backdrop-filter:blur(18px) saturate(145%);backdrop-filter:blur(18px) saturate(145%)}
  .report-navigation-track{display:flex;align-items:center;gap:4px;min-width:0;flex:1;overflow-x:auto;scrollbar-width:none}
  .report-navigation-track::-webkit-scrollbar{display:none}
  .report-nav-link{display:inline-flex;align-items:center;gap:7px;flex:0 0 auto;min-height:38px;padding:0 11px;border:1px solid transparent;border-radius:11px;color:var(--muted);font-size:11px;font-weight:760;text-decoration:none;white-space:nowrap;transition:background-color .16s ease,border-color .16s ease,color .16s ease,transform .16s ease}
  .report-nav-link:hover{border-color:rgba(0,175,165,.18);background:rgba(255,255,255,.62);color:var(--ink);transform:translateY(-1px)}
  .report-nav-link.active,.report-nav-link[aria-current="page"]{border-color:rgba(0,175,165,.26);background:linear-gradient(135deg,rgba(232,248,246,.92),rgba(236,248,255,.88));color:var(--teal-dark);box-shadow:0 5px 14px rgba(0,126,120,.09)}
  .report-nav-icon{width:16px;height:16px;flex:0 0 16px}
  .report-theme-toggle{display:inline-flex;align-items:center;justify-content:center;gap:7px;flex:0 0 auto;min-height:38px;padding:0 11px;border:1px solid rgba(119,145,158,.26);border-radius:11px;background:rgba(255,255,255,.6);color:var(--ink);font-size:11px;font-weight:760;box-shadow:inset 0 1px 0 rgba(255,255,255,.72)}
  .report-theme-toggle:hover{border-color:rgba(0,175,165,.38);background:rgba(255,255,255,.82)}
  .theme-toggle-icon{width:16px;height:16px}.theme-toggle-label{white-space:nowrap}
  html[data-report-theme="light"] [data-theme-icon-light],html[data-report-theme="dark"] [data-theme-icon-dark]{display:none}

  .rich-step-item .step-title-row{grid-template-columns:32px minmax(0,1fr) 29px auto}
  .step-hook-info-button{display:inline-grid;place-items:center;align-self:start;flex:0 0 29px;width:29px;height:29px;margin:1px 0 0;border:1px solid rgba(0,175,165,.22);border-radius:9px;background:linear-gradient(145deg,rgba(232,248,246,.88),rgba(236,248,255,.72));color:var(--teal-dark);box-shadow:inset 0 1px 0 rgba(255,255,255,.8)}
  .step-hook-info-button:hover{border-color:rgba(0,175,165,.5);background:var(--teal-soft)}
  .step-hook-info-button:focus-visible,.report-theme-toggle:focus-visible,.report-nav-link:focus-visible{outline:3px solid rgba(0,175,165,.18);outline-offset:2px}
  .scenario-hooks-disclosure + .scenario-hooks-disclosure{margin-top:8px}

  .attachment-visual-card{padding:0;text-align:left;transition:transform .16s ease,border-color .16s ease,box-shadow .16s ease}
  .attachment-visual-card:hover{transform:translateY(-2px);border-color:var(--attachment-stage-line);box-shadow:0 12px 26px rgba(20,70,80,.12)}
  .attachment-visual-stage{display:grid;place-items:center;width:100%;min-height:220px;max-height:480px;padding:14px;overflow:hidden;border-bottom:1px solid var(--attachment-stage-line);background:radial-gradient(circle at 50% 24%,rgba(255,255,255,.85),transparent 48%),linear-gradient(145deg,var(--attachment-stage),rgba(56,189,248,.08))}
  .attachment-visual-stage .attachment-image{display:block;width:100%;height:auto;max-width:100%;max-height:440px;object-fit:contain;background:transparent}
  .attachment-visual-stage .video-attachment-preview{display:grid;place-items:center;width:100%;min-height:190px;height:auto;border-radius:8px;background:linear-gradient(145deg,#10283A,#173247)}
  .attachment-button .attachment-caption{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 12px;color:var(--ink);font-size:11px;font-weight:700;overflow-wrap:anywhere}
  .attachment-caption small{flex:0 0 auto;color:var(--teal-dark);font-size:10px;font-weight:800;text-transform:uppercase;letter-spacing:.045em}

  html[data-report-theme="dark"]{color-scheme:dark;--page:#07111C;--surface:#0D1B29;--surface-soft:#112231;--surface-glass:rgba(13,27,41,.9);--theme-glass:rgba(13,27,41,.84);--nav-glass:rgba(11,25,38,.76);--ink:#E8F0F6;--muted:#9BAEBE;--line:#263B4B;--line-strong:#355064;--teal:#43D5CB;--teal-dark:#76E5DD;--teal-soft:#123A3A;--cyan-soft:#102F45;--violet-soft:#292144;--attachment-stage:#112B35;--attachment-stage-line:#28515A;--shadow:0 10px 28px rgba(0,0,0,.2)}
  html[data-report-theme="dark"],html[data-report-theme="dark"] body{background-color:var(--page)}
  html[data-report-theme="dark"] body{background:radial-gradient(circle at 7% 0,rgba(67,213,203,.11),transparent 27%),radial-gradient(circle at 94% 2%,rgba(139,92,246,.13),transparent 25%),linear-gradient(rgba(148,163,184,.035) 1px,transparent 1px),linear-gradient(90deg,rgba(148,163,184,.035) 1px,transparent 1px),linear-gradient(180deg,#07131E,#081522 360px,#07111C);background-size:auto,auto,28px 28px,28px 28px,auto}
  html[data-report-theme="dark"] .glass-card,html[data-report-theme="dark"] .metric-card,html[data-report-theme="dark"] .results-card{background:var(--theme-glass);box-shadow:0 12px 30px rgba(0,0,0,.18),inset 0 1px 0 rgba(255,255,255,.035)}
  html[data-report-theme="dark"] .status-chart-card{background:linear-gradient(140deg,rgba(13,27,41,.96),rgba(13,35,43,.9) 72%,rgba(41,33,68,.5))}
  html[data-report-theme="dark"] .report-navigation{border-color:rgba(92,123,139,.34);background:linear-gradient(135deg,var(--nav-glass),rgba(17,34,49,.68));box-shadow:0 15px 36px rgba(0,0,0,.24),inset 0 1px 0 rgba(255,255,255,.04)}
  html[data-report-theme="dark"] .report-nav-link:hover,html[data-report-theme="dark"] .report-theme-toggle{background:rgba(255,255,255,.045)}
  html[data-report-theme="dark"] .report-nav-link.active,html[data-report-theme="dark"] .report-nav-link[aria-current="page"]{background:linear-gradient(135deg,rgba(18,58,58,.94),rgba(16,47,69,.84));color:#A0F0EA}
  html[data-report-theme="dark"] .report-theme-toggle{border-color:rgba(137,164,177,.28);color:var(--ink);box-shadow:inset 0 1px 0 rgba(255,255,255,.04)}
  html[data-report-theme="dark"] .control-dock,html[data-report-theme="dark"] .scenario-detail-toolbar{background:rgba(13,27,41,.95)}
  html[data-report-theme="dark"] .search-shell input,html[data-report-theme="dark"] .control-label select,html[data-report-theme="dark"] .filter-button,html[data-report-theme="dark"] .clear-icon-button,html[data-report-theme="dark"] .context-table-shell,html[data-report-theme="dark"] .coverage-group,html[data-report-theme="dark"] .scenario-table-shell,html[data-report-theme="dark"] .step-data-table-shell,html[data-report-theme="dark"] .attachment-card,html[data-report-theme="dark"] .step-item,html[data-report-theme="dark"] .hook-card,html[data-report-theme="dark"] .output-list{background:var(--surface);color:var(--ink);border-color:var(--line)}
  html[data-report-theme="dark"] .context-table th,html[data-report-theme="dark"] .coverage-head,html[data-report-theme="dark"] .step-data-table tr:first-child td,html[data-report-theme="dark"] [data-report-page="scenarios"] .scenario-table thead th{background:var(--surface-soft)}
  html[data-report-theme="dark"] .clean-explorer-controls,html[data-report-theme="dark"] .clean-table-shell,html[data-report-theme="dark"] .clean-scenario-table,html[data-report-theme="dark"] .clean-scenario-table tr{background:var(--surface)!important;color:var(--ink)}
  html[data-report-theme="dark"] .clean-scenario-table th{background:var(--surface-soft)!important;color:var(--muted)}
  html[data-report-theme="dark"] .clean-scenario-table tbody tr[data-open-scenario]:hover{background:#102C30!important}
  html[data-report-theme="dark"] .clean-search-shell input,html[data-report-theme="dark"] .explorer-filter-grid .control-label select{background:var(--surface)!important;color:var(--ink);border-color:var(--line-strong)}
  html[data-report-theme="dark"] .explorer-filter-grid{border-top-color:var(--line)}
  html[data-report-theme="dark"] .explorer-empty-state{border-color:var(--line-strong);background:linear-gradient(145deg,rgba(18,58,58,.38),rgba(13,27,41,.92))}
  html[data-report-theme="dark"] .step-number{border-color:#28515A;background:linear-gradient(180deg,#143638,#102A31);box-shadow:0 2px 6px rgba(0,0,0,.16),inset 0 1px 0 rgba(255,255,255,.04);color:#A0F0EA}
  html[data-report-theme="dark"] .hook-location,html[data-report-theme="dark"] .dialog-context span{color:var(--muted)}
  html[data-report-theme="dark"] .hook-arguments span{background:#17303D;color:#B8C9D5}
  html[data-report-theme="dark"] .scenario-hooks-disclosure{border-color:var(--line);background:#0F1E2C}
  html[data-report-theme="dark"] .scenario-hooks-body{border-top-color:var(--line)}
  html[data-report-theme="dark"] .error-disclosure{border-color:#6D3038;background:#2A171B}
  html[data-report-theme="dark"] .error-summary{color:#FCA5A5}
  html[data-report-theme="dark"] .error-scroll{border-top-color:#6D3038!important}
  html[data-report-theme="dark"] .report-dialog{background:var(--surface);color:var(--ink);box-shadow:0 30px 90px rgba(0,0,0,.52)}
  html[data-report-theme="dark"] .dialog-header,html[data-report-theme="dark"] .media-toolbar{background:var(--surface-soft)}
  html[data-report-theme="dark"] .icon-button,html[data-report-theme="dark"] .media-tool,html[data-report-theme="dark"] .step-hook-group{background:var(--surface);color:var(--ink);border-color:var(--line)}
  html[data-report-theme="dark"] .dialog-context{background:var(--teal-soft)}
  html[data-report-theme="dark"] .hotspot-tabs{background:#10202E}
  html[data-report-theme="dark"] .hotspot-tab.active{background:#172B3A;color:#A0F0EA;box-shadow:0 2px 7px rgba(0,0,0,.2)}
  html[data-report-theme="dark"] .radial-chart:before{background:var(--surface)}
  html[data-report-theme="dark"] .mini-progress,html[data-report-theme="dark"] .bar-track,html[data-report-theme="dark"] .status-stack{background:#203544}
  html[data-report-theme="dark"] .scenario-table tbody tr[data-open-scenario]:hover{background:#102C30}
  html[data-report-theme="dark"] .back-button{background:linear-gradient(135deg,#102D31,#102633);border-color:#27545A;color:#A0F0EA}
  html[data-report-theme="dark"] .step-hook-info-button{border-color:rgba(67,213,203,.3);background:linear-gradient(145deg,rgba(18,58,58,.94),rgba(16,47,69,.78));color:#A0F0EA;box-shadow:inset 0 1px 0 rgba(255,255,255,.04)}
  html[data-report-theme="dark"] .attachment-visual-stage{background:radial-gradient(circle at 50% 24%,rgba(255,255,255,.055),transparent 48%),linear-gradient(145deg,var(--attachment-stage),rgba(56,189,248,.05))}
  html[data-report-theme="dark"] .attachment-caption small{color:#76E5DD}

  @media(max-width:760px){
    .report-navigation{top:6px;display:grid;grid-template-columns:minmax(0,1fr) auto;gap:7px;padding:7px;border-radius:14px}
    .report-navigation-track{gap:3px;overscroll-behavior-x:contain;scroll-snap-type:x proximity}
    .report-nav-link{min-height:36px;padding:0 9px;scroll-snap-align:start}
    .report-nav-link span{display:none}
    .report-nav-icon{width:17px;height:17px;flex-basis:17px}
    .report-theme-toggle{width:38px;height:38px;min-height:38px;padding:0;border-radius:10px}
    .theme-toggle-label{display:none}
    .attachment-visual-stage{min-height:190px;padding:10px}
  }
  @media(max-width:620px){
    .rich-step-item .step-title-row{grid-template-columns:32px minmax(0,1fr) 29px}
    .rich-step-item .step-duration{grid-column:2/span 2;padding-top:0;margin-top:2px;text-align:left}
  }
</style>
<script>
(function () {
  var storageKey = 'testara-report-theme';
  var root = document.documentElement;
  var stored = null;
  try { stored = localStorage.getItem(storageKey); } catch (ignored) {}
  var systemDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  var initial = stored === 'dark' || stored === 'light' ? stored : (systemDark ? 'dark' : 'light');
  root.dataset.reportTheme = initial;

  function syncThemeControl(button) {
    if (!button) return;
    var dark = root.dataset.reportTheme === 'dark';
    button.setAttribute('aria-label', dark ? 'Switch to light theme' : 'Switch to dark theme');
    button.setAttribute('title', dark ? 'Switch to light theme' : 'Switch to dark theme');
    var label = button.querySelector('[data-theme-label]');
    if (label) label.textContent = dark ? 'Light' : 'Dark';
  }

  document.addEventListener('DOMContentLoaded', function () {
    var button = document.querySelector('[data-theme-toggle]');
    syncThemeControl(button);
    if (!button) return;
    button.addEventListener('click', function () {
      var next = root.dataset.reportTheme === 'dark' ? 'light' : 'dark';
      root.dataset.reportTheme = next;
      try { localStorage.setItem(storageKey, next); } catch (ignored) {}
      syncThemeControl(button);
    });
  });
})();
</script>