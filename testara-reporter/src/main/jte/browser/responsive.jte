<style>
  @@media(max-width:620px){
    body{background:radial-gradient(circle at 10% 0,rgba(0,175,165,.12),transparent 24%),radial-gradient(circle at 100% 5%,rgba(139,92,246,.10),transparent 24%),linear-gradient(180deg,#F4FAFA,#F6F8FB)}
    .report-page{width:calc(100% - 16px)}
    .results-heading{align-items:flex-start;flex-direction:column;gap:5px}
    .result-count{align-self:flex-start}
    .control-dock{padding:9px;margin:14px 0 10px;border-radius:11px}
    .control-primary{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:7px;align-items:stretch}
    .search-shell{grid-column:1 / -1;min-width:0}
    .search-shell input{height:42px;min-width:0;font-size:16px}
    .mobile-filter-toggle{display:inline-flex;align-items:center;justify-content:center}
    .failed-chip,.filter-button{width:100%;min-width:0;height:38px;padding:0 8px;justify-content:center;font-size:11px}
    .failed-chip{overflow:hidden}.failed-chip span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
    .control-secondary{display:none;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin-top:9px;padding-top:9px;border-top:1px solid #DDE8EA}
    .control-secondary.is-open{display:grid}
    .control-label{min-width:0}.control-label select{height:40px;min-width:0;font-size:16px}
    .mobile-sort-strip{display:flex;align-items:center;gap:6px;width:100%;margin:7px 0 10px;padding:2px 1px 5px;overflow-x:auto;overscroll-behavior-inline:contain;scrollbar-width:none}
    .mobile-sort-strip::-webkit-scrollbar{display:none}
    .mobile-sort-label{flex:0 0 auto;color:var(--muted);font-size:10px;font-weight:800;letter-spacing:.06em;text-transform:uppercase;margin-right:2px}
    .mobile-sort-button{flex:0 0 auto;min-height:34px;padding:6px 10px;border:1px solid var(--line-strong);border-radius:999px;background:rgba(255,255,255,.9);color:var(--muted);font-size:11px;font-weight:800;letter-spacing:0;text-transform:none;box-shadow:0 2px 7px rgba(22,44,64,.04)}
    .mobile-sort-button.active{border-color:rgba(0,175,165,.42);background:linear-gradient(135deg,var(--teal-soft),var(--cyan-soft));color:var(--teal-dark)}
    .scenario-table td{grid-template-columns:88px minmax(0,1fr);gap:8px;padding:6px 10px;overflow-wrap:anywhere}
    .scenario-table td:last-child{padding-top:10px}
    .detail-button{width:100%;min-height:36px}
    .detail-panel{padding:14px 12px}
    .step-item{grid-template-columns:10px minmax(0,1fr);gap:8px}
    .step-duration{grid-column:2;justify-self:start}
  }
  @@media(max-width:420px){
    .card-pad{padding:16px 14px}
    .control-secondary{grid-template-columns:1fr}
    .failed-chip,.filter-button{font-size:10.5px;padding:0 6px}
    .scenario-table td{grid-template-columns:78px minmax(0,1fr)}
    .context-table th,.context-table td{padding:10px 9px}
    .context-table th{width:42%}
  }
</style>