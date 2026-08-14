<style>
  .clean-explorer-controls{padding:12px;border:1px solid var(--line);border-radius:12px;background:#FFF!important;box-shadow:0 5px 16px rgba(22,44,64,.05)!important;backdrop-filter:none!important;-webkit-backdrop-filter:none!important}
  .explorer-search-row{width:100%}
  .clean-search-shell{position:relative;width:100%}
  .clean-search-shell .search-icon{left:13px;color:#708090}
  .clean-search-shell input{width:100%;height:42px;padding:0 42px 0 40px;border:1px solid var(--line-strong);border-radius:9px;background:#FFF;color:var(--ink);outline:none;font-size:13px}
  .clean-search-shell input:focus{border-color:var(--teal);box-shadow:0 0 0 3px rgba(0,175,165,.1)}
  .inline-clear-button{position:absolute;right:7px;top:50%;transform:translateY(-50%);width:29px;height:29px;display:grid;place-items:center;padding:0;border:0;border-radius:7px;background:transparent;color:#748291}
  .inline-clear-button:hover{background:#F0F6F7;color:var(--teal-dark)}
  .inline-clear-button:focus-visible{outline:3px solid rgba(0,175,165,.15);outline-offset:1px}
  .inline-clear-button[hidden]{display:none}
  .explorer-filter-grid{display:grid!important;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px;margin-top:10px;padding-top:10px;border-top:1px solid #E7EDF1}
  .explorer-filter-grid .control-label span{margin-left:1px}
  .explorer-filter-grid .control-label select{height:38px;background:#FFF}

  .clean-table-shell{width:100%;overflow-x:auto!important;overflow-y:visible;border:1px solid var(--line)!important;border-radius:11px!important;background:#FFF!important;scrollbar-gutter:stable;-webkit-overflow-scrolling:touch}
  .clean-table-shell:focus-visible{outline:3px solid rgba(0,175,165,.13);outline-offset:2px}
  .clean-scenario-table{display:table!important;width:100%!important;min-width:820px!important;border-collapse:collapse!important;border-spacing:0!important;table-layout:auto!important;background:#FFF}
  .clean-scenario-table thead{display:table-header-group!important}
  .clean-scenario-table tbody{display:table-row-group!important}
  .clean-scenario-table tr{display:table-row!important;width:auto!important;border:0!important;border-radius:0!important;padding:0!important;box-shadow:none!important;background:#FFF}
  .clean-scenario-table tr[hidden]{display:none!important}
  .clean-scenario-table th,.clean-scenario-table td{display:table-cell!important;width:auto!important;padding:12px 14px!important;border:0!important;border-bottom:1px solid var(--line)!important;text-align:left;vertical-align:middle}
  .clean-scenario-table th{background:#F7FAFB!important;color:#657586;font-size:10px;font-weight:800;letter-spacing:.055em;text-transform:uppercase;white-space:nowrap}
  .clean-scenario-table th:nth-child(1){width:116px}
  .clean-scenario-table th:nth-child(2){min-width:260px}
  .clean-scenario-table th:nth-child(3){min-width:190px}
  .clean-scenario-table th:nth-child(4){min-width:230px}
  .clean-scenario-table th:nth-child(5){width:112px;text-align:right}
  .clean-scenario-table td:nth-child(5){text-align:right;white-space:nowrap}
  .clean-scenario-table tbody tr[data-open-scenario]{cursor:pointer;transition:background-color .14s ease,box-shadow .14s ease}
  .clean-scenario-table tbody tr[data-open-scenario]:hover{background:#F5FBFB!important;box-shadow:inset 3px 0 0 var(--teal)!important}
  .clean-scenario-table tbody tr[data-open-scenario]:hover td{background:transparent!important}
  .clean-scenario-table tbody tr[data-open-scenario]:focus-visible{outline:3px solid rgba(0,175,165,.16);outline-offset:-3px;position:relative;z-index:1}
  .clean-scenario-table .sort-header{display:inline-flex;align-items:center;gap:5px;border:0;background:transparent;padding:0;color:inherit;font:inherit;font-weight:800;text-transform:inherit;letter-spacing:inherit;white-space:nowrap}
  .clean-scenario-table .sort-header:hover,.clean-scenario-table .sort-header.active{color:var(--teal-dark)}
  .clean-scenario-table .scenario-name{display:block;font-size:13px;font-weight:750;color:var(--ink)}
  .clean-scenario-table .scenario-meta{display:block;margin-top:2px;color:var(--muted);font-size:11px;line-height:1.35}

  .explorer-empty-state{margin:18px 0 0;padding:34px 24px;border:1px dashed rgba(11,18,32,.18);border-radius:14px;background:linear-gradient(145deg,rgba(232,248,246,.68),rgba(255,255,255,.92));text-align:center;color:var(--muted)}
  .explorer-empty-state[hidden]{display:none!important}
  .explorer-empty-state strong{display:block;color:var(--ink);font-size:15px;font-weight:760}
  .explorer-empty-state p{margin:5px 0 0;font-size:12px}

  .rich-step-list{counter-reset:scenario-step}
  .rich-step-item{counter-increment:scenario-step}
  .step-number{display:inline-grid;place-items:center;width:26px;height:26px;flex:0 0 26px;border:1px solid rgba(0,175,165,.2);border-radius:8px;background:rgba(0,175,165,.09);color:var(--teal-dark);font-size:12px;font-weight:800;line-height:1}
  .step-number::before{content:counter(scenario-step)}

@@media(max-width:760px){
  .results-heading{align-items:flex-start}
  .explorer-filter-grid{grid-template-columns:repeat(2,minmax(0,1fr))!important}
  .clean-table-shell{overflow-x:auto!important;max-width:100%}
  .clean-scenario-table{display:table!important;width:100%!important;min-width:760px!important;border-collapse:collapse!important;border-spacing:0!important}
  .clean-scenario-table thead{display:table-header-group!important}
  .clean-scenario-table tbody{display:table-row-group!important}
  .clean-scenario-table tr{display:table-row!important;width:auto!important;padding:0!important;border:0!important;border-radius:0!important;box-shadow:none!important}
  .clean-scenario-table tr[hidden]{display:none!important}
  .clean-scenario-table th,.clean-scenario-table td{display:table-cell!important;width:auto!important;padding:10px 11px!important;border-bottom:1px solid var(--line)!important}
  .clean-scenario-table td:before{content:none!important;display:none!important}
}

@@media(max-width:420px){
  .clean-explorer-controls{padding:10px}
  .explorer-filter-grid{grid-template-columns:1fr 1fr!important;gap:7px}
  .clean-search-shell input{font-size:12px}
  .clean-scenario-table{min-width:720px!important}
}
</style>
