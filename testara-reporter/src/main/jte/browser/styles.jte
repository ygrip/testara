<style>
  :root{
    color-scheme:light;
    --page:#F5F8FB;
    --surface:#FFFFFF;
    --surface-soft:#F8FBFC;
    --surface-glass:rgba(255,255,255,.9);
    --ink:#0B1220;
    --muted:#627083;
    --line:#D9E2E8;
    --line-strong:#C8D4DC;
    --teal:#00AFA5;
    --teal-dark:#007E78;
    --teal-soft:#E8F8F6;
    --mint:#5EDCCF;
    --blue:#3B82F6;
    --green:#22C55E;
    --red:#EF4444;
    --amber:#F59E0B;
    --slate:#64748B;
    --radius:16px;
    --radius-sm:10px;
    --shadow:0 8px 24px rgba(22,44,64,.06);
    --body-font:Inter,ui-sans-serif,-apple-system,BlinkMacSystemFont,"Segoe UI",Arial,Helvetica,sans-serif;
    --display-font:"Segoe UI Variable",Inter,ui-sans-serif,-apple-system,BlinkMacSystemFont,"Segoe UI",Arial,Helvetica,sans-serif;
  }
  *{box-sizing:border-box}
  html{scroll-behavior:smooth;background:var(--page)}
  body{margin:0;min-width:320px;color:var(--ink);font-family:var(--body-font);font-size:15px;line-height:1.5;background:linear-gradient(180deg,#F0FAF9 0,#F5F8FB 260px,#F5F8FB 100%);font-variant-numeric:tabular-nums;-webkit-font-smoothing:antialiased}
  button,input,select{font:inherit}
  button{cursor:pointer}
  h1,h2,h3,p{margin:0}
  h1,h2,h3{font-family:var(--display-font);letter-spacing:-.02em;text-wrap:balance}
  .report-page{width:min(1200px,calc(100% - 32px));margin:0 auto;padding:28px 0 44px}
  .bento-grid{display:grid;grid-template-columns:repeat(12,minmax(0,1fr));gap:14px;margin-top:14px;align-items:stretch}
  .span-12{grid-column:span 12}.span-8{grid-column:span 8}.span-7{grid-column:span 7}.span-6{grid-column:span 6}.span-5{grid-column:span 5}.span-4{grid-column:span 4}

  .glass-card{position:relative;overflow:hidden;border:1px solid var(--line);border-radius:var(--radius);background:var(--surface-glass);box-shadow:var(--shadow);-webkit-backdrop-filter:blur(10px);backdrop-filter:blur(10px)}
  .glass-card:before{content:"";position:absolute;top:0;left:0;right:0;height:3px;background:linear-gradient(90deg,var(--teal),rgba(0,175,165,.16) 45%,transparent 78%)}
  .card-pad{padding:22px 24px}
  .card-eyebrow{font-size:11px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:var(--teal-dark)}
  .danger-eyebrow{color:#B91C1C}.warning-eyebrow{color:#B45309}
  .card-title{font-size:20px;line-height:1.25;font-weight:740;margin-top:3px}
  .card-subtitle{font-size:13px;line-height:1.5;color:var(--muted);margin-top:4px;max-width:820px}
  .card-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:18px}
  .compact-head{margin-bottom:14px}
  .icon-tile{width:36px;height:36px;display:grid;place-items:center;flex:0 0 36px;border-radius:10px;border:1px solid #BFEAE6;background:var(--teal-soft);color:var(--teal-dark);font-size:17px;font-weight:800}
  .icon-tile.danger{color:#B91C1C;background:#FEF2F2;border-color:#FECACA}
  .icon-tile.info{color:#1D4ED8;background:#EFF6FF;border-color:#BFDBFE}

  .hero{position:relative;overflow:hidden;border:1px solid #17364B;border-radius:20px;padding:28px 30px;color:#F8FAFC;background:linear-gradient(118deg,#0B1828 0%,#10283A 62%,#123E46 100%);box-shadow:0 14px 34px rgba(11,24,40,.14)}
  .hero:before{content:"";position:absolute;left:0;top:0;bottom:0;width:5px;background:linear-gradient(180deg,var(--teal),var(--mint))}
  .hero:after{content:"";position:absolute;width:260px;height:260px;right:-110px;top:-150px;border-radius:50%;background:radial-gradient(circle,rgba(94,220,207,.16),transparent 68%)}
  .hero-grid{position:relative;z-index:1;display:grid;grid-template-columns:minmax(0,1.6fr) minmax(250px,.62fr);gap:28px;align-items:end}
  .brand-row{display:flex;align-items:center;gap:12px;margin-bottom:18px}.brand-logo{display:block;max-width:150px;max-height:40px;width:auto;height:auto;object-fit:contain}.brand-name{font-size:12px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:#9FE7DF}
  .hero h1{font-size:clamp(30px,4vw,43px);line-height:1.08;font-weight:760;max-width:760px}
  .hero-copy{max-width:720px;color:#C9D6E2;font-size:14px;margin-top:10px}
  .hero-badges{display:flex;flex-wrap:wrap;gap:8px;margin-top:18px}.hero-chip{display:inline-flex;align-items:center;gap:7px;padding:6px 9px;border:1px solid rgba(226,232,240,.15);border-radius:8px;background:rgba(255,255,255,.055);color:#DCE5EF;font-size:12px}
  .overall-panel{position:relative;border:1px solid rgba(190,230,226,.2);border-radius:14px;padding:18px;background:rgba(255,255,255,.07);box-shadow:inset 0 1px 0 rgba(255,255,255,.08);-webkit-backdrop-filter:blur(8px);backdrop-filter:blur(8px)}
  .overall-label{font-size:11px;letter-spacing:.08em;text-transform:uppercase;font-weight:800;color:#9FE7DF}.overall-value{font-size:36px;line-height:1.08;font-weight:800;margin-top:5px}.overall-caption{font-size:12px;color:#CBD5E1;margin-top:4px}.overall-status{display:inline-flex;margin-top:13px;padding:5px 8px;border-radius:7px;font-size:11px;font-weight:800;letter-spacing:.04em;border:1px solid rgba(255,255,255,.14)}
  .status-PASSED{color:#86EFAC;background:rgba(34,197,94,.12)}.status-FAILED{color:#FCA5A5;background:rgba(239,68,68,.12)}.status-PENDING{color:#FCD34D;background:rgba(245,158,11,.12)}

  .metric-card{min-height:158px;background:var(--surface)}
  .metric-top{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}
  .metric-label{font-size:11px;text-transform:uppercase;letter-spacing:.07em;font-weight:800;color:var(--muted)}
  .metric-value{font-family:var(--display-font);font-size:32px;line-height:1.08;font-weight:780;letter-spacing:-.03em;margin-top:7px}
  .metric-value.compact{font-size:22px;line-height:1.2}
  .metric-value.teal{color:var(--teal-dark)}
  .metric-note{font-size:13px;color:var(--muted);margin-top:6px}
  .mini-progress{height:7px;border-radius:999px;background:#E2E8EE;overflow:hidden;margin-top:15px}.mini-progress>span{display:block;height:100%;border-radius:inherit;background:var(--teal)}

  .status-chart-card{min-height:320px;background:var(--surface)}
  .chart-layout{display:grid;grid-template-columns:minmax(250px,.72fr) minmax(0,1.28fr);gap:34px;align-items:center;max-width:980px;margin:0 auto}
  .chart-primary{text-align:center}.chart-secondary{min-width:0}
  .radial-chart{width:220px;height:220px;margin:auto;border-radius:50%;position:relative;display:grid;place-items:center;background:conic-gradient(var(--green) 0 var(--pass),#E1E8ED var(--pass) 100%)}
  .radial-chart:before{content:"";position:absolute;inset:27px;border-radius:50%;background:#FFFFFF;box-shadow:inset 0 0 0 1px var(--line)}
  .radial-center{position:relative;text-align:center}.radial-center strong{display:block;font-family:var(--display-font);font-size:40px;line-height:1;font-weight:800;letter-spacing:-.04em}.radial-center span{display:block;font-size:12px;color:var(--muted);margin-top:6px}
  .chart-voice{margin:14px auto 0;max-width:330px;padding:10px 12px;border-left:3px solid var(--teal);background:var(--teal-soft);font-size:13px;color:#28555A;text-align:left}.chart-voice strong{color:var(--teal-dark)}
  .status-stack{height:18px;display:flex;overflow:hidden;border-radius:5px;background:#DDE6EC;box-shadow:inset 0 1px 2px rgba(15,23,42,.05);margin:8px 0 20px}.status-stack span{height:100%;min-width:0}
  .legend-list{display:grid;gap:0;border:1px solid var(--line);border-radius:10px;overflow:hidden}.legend-row{display:grid;grid-template-columns:12px 1fr auto auto;gap:10px;align-items:center;padding:11px 12px;border-bottom:1px solid var(--line);font-size:13px}.legend-row:last-child{border-bottom:0}.legend-dot{width:9px;height:9px;border-radius:50%}.legend-row b{font-size:14px}.legend-row small{color:var(--muted);text-align:right;min-width:46px}

  .context-table-shell{border:1px solid var(--line);border-radius:10px;overflow:hidden;background:#FFFFFF}
  .context-table{width:100%;border-collapse:collapse;table-layout:fixed}
  .context-table th,.context-table td{padding:12px 14px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top;font-size:13px;overflow-wrap:anywhere}
  .context-table tr:last-child th,.context-table tr:last-child td{border-bottom:0}
  .context-table th{width:28%;background:var(--surface-soft);color:var(--muted);font-size:11px;text-transform:uppercase;letter-spacing:.055em;font-weight:800}
  .context-table td{font-weight:650;color:var(--ink)}

  .rank-list{display:grid;gap:14px}.wide-rank-list{max-width:none}.rank-item{min-width:0}.rank-head{display:flex;justify-content:space-between;gap:16px;align-items:baseline;font-size:13px}.rank-head span{font-weight:700;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.rank-head strong{font-size:12px;white-space:nowrap}.danger-value{color:#B91C1C}
  .bar-track{height:9px;border-radius:4px;background:#E1E8EE;overflow:hidden;margin-top:7px}.bar-track>span{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,var(--teal),var(--mint))}.bar-track.danger>span{background:linear-gradient(90deg,#DC2626,#F87171)}
  .rank-item small{display:block;color:var(--muted);font-size:11px;margin-top:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.signal-row small{white-space:normal;overflow:visible}
  .subsection-block{margin-top:22px;padding-top:18px;border-top:1px solid var(--line)}.subsection-list{margin-top:10px}

  .coverage-grid{display:grid;grid-template-columns:1fr;gap:12px}.coverage-full{width:100%}
  .coverage-group{border:1px solid var(--line);border-radius:10px;overflow:hidden;background:#FFFFFF}
  .coverage-head{display:flex;justify-content:space-between;gap:12px;padding:11px 14px;background:var(--surface-soft);border-bottom:1px solid var(--line);font-size:12px}
  .coverage-row{display:grid;grid-template-columns:minmax(0,1fr) minmax(220px,32%);gap:24px;align-items:center;padding:12px 14px;border-bottom:1px solid var(--line)}.coverage-row:last-child{border-bottom:0}
  .coverage-copy strong{display:block;font-size:13px}.coverage-copy small{display:block;font-size:11px;color:var(--muted);margin-top:2px}.coverage-rate strong{display:block;text-align:right;color:var(--teal-dark);font-size:12px}

  .results-card{overflow:visible;background:var(--surface)}
  .results-heading{display:flex;justify-content:space-between;gap:18px;align-items:flex-end}.results-heading p{color:var(--muted);font-size:13px;margin-top:4px}.result-count{font-size:12px;color:var(--muted)}
  .control-dock{position:sticky;top:10px;z-index:8;margin:18px 0 14px;padding:10px 12px;border:1px solid var(--line-strong);border-radius:12px;background:rgba(255,255,255,.94);box-shadow:0 8px 22px rgba(22,44,64,.08);-webkit-backdrop-filter:blur(8px);backdrop-filter:blur(8px)}
  .control-primary{display:grid;grid-template-columns:minmax(300px,1fr) auto auto;gap:9px;align-items:center}
  .control-secondary{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px;margin-top:9px;padding-top:9px;border-top:1px solid #E5ECEF}
  .search-shell{position:relative}.search-icon{position:absolute;left:12px;top:50%;transform:translateY(-50%);color:var(--muted);font-size:18px;pointer-events:none}.search-shell input{width:100%;height:40px;border:1px solid var(--line-strong);border-radius:9px;background:#FFFFFF;color:var(--ink);padding:0 12px 0 38px;outline:none}.search-shell input:focus,.control-label select:focus{border-color:var(--teal);box-shadow:0 0 0 3px rgba(0,175,165,.1)}
  .control-label span{display:block;font-size:10px;font-weight:800;letter-spacing:.055em;text-transform:uppercase;color:var(--muted);margin:0 0 4px 2px}.control-label select{width:100%;height:38px;border:1px solid var(--line-strong);border-radius:8px;background:#FFFFFF;color:var(--ink);padding:0 10px;outline:none;font-size:13px}
  .failed-chip{height:40px;display:inline-flex;align-items:center;gap:7px;padding:0 12px;border:1px solid #FECACA;border-radius:9px;background:#FFF7F7;color:#B91C1C;font-size:12px;font-weight:750;white-space:nowrap}.failed-chip input{accent-color:#DC2626}
  .filter-button{height:40px;border:1px solid var(--line-strong);border-radius:9px;padding:0 13px;background:#FFFFFF;color:var(--ink);font-size:12px;font-weight:750}.filter-button:hover{border-color:var(--teal);color:var(--teal-dark);background:#F4FBFA}

  .scenario-table-shell{overflow-x:auto;border:1px solid var(--line);border-radius:10px;background:#FFFFFF}
  .scenario-table{width:100%;border-collapse:collapse;min-width:880px}.scenario-table th,.scenario-table td{padding:11px 12px;border-bottom:1px solid var(--line);text-align:left;vertical-align:middle;font-size:13px}.scenario-table th{background:var(--surface-soft);font-size:10px;letter-spacing:.055em;text-transform:uppercase;color:var(--muted);font-weight:800}.scenario-table tbody tr:last-child td{border-bottom:0}.scenario-table tbody tr[data-scenario-row]:hover td{background:#FAFDFC}
  .sort-header{display:inline-flex;align-items:center;gap:5px;border:0;padding:0;background:transparent;color:inherit;font:inherit;font-weight:inherit;letter-spacing:inherit;text-transform:inherit}.sort-header:hover,.sort-header.active{color:var(--teal-dark)}.sort-header [data-sort-indicator]{font-size:12px;line-height:1}
  .status-pill{display:inline-flex;padding:4px 7px;border-radius:6px;border:1px solid;font-size:10px;font-weight:800;letter-spacing:.035em}.status-passed{color:#15803D;background:#F0FDF4;border-color:#BBF7D0}.status-failed{color:#B91C1C;background:#FEF2F2;border-color:#FECACA}.status-skipped{color:#475569;background:#F8FAFC;border-color:#CBD5E1}.status-pending,.status-undefined{color:#B45309;background:#FFFBEB;border-color:#FDE68A}
  .scenario-name{display:block;font-weight:700;color:var(--ink)}.scenario-meta{display:block;color:var(--muted);font-size:11px;margin-top:2px}.detail-button{border:1px solid #B9DAD7;border-radius:7px;background:#F2FBFA;color:var(--teal-dark);padding:6px 9px;font-size:11px;font-weight:750;white-space:nowrap}.detail-button:hover{background:#E3F7F5;border-color:var(--teal)}
  .detail-row td{padding:0!important;background:#F8FBFC}.detail-panel{padding:16px 18px;border-left:3px solid var(--teal)}.detail-grid{display:grid;grid-template-columns:minmax(0,1.3fr) minmax(280px,.7fr);gap:18px;margin-top:10px}.step-list{display:grid;gap:6px}.step-item{display:grid;grid-template-columns:10px minmax(0,1fr) auto;gap:9px;align-items:start;padding:8px 9px;border:1px solid var(--line);border-radius:8px;background:#FFFFFF}.step-dot{width:8px;height:8px;border-radius:50%;margin-top:5px}.step-item strong{font-size:12px}.step-error{display:block;color:#B91C1C;margin-top:2px}.step-duration{color:var(--muted);white-space:nowrap}.error-box{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;font:12px/1.55 ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace;background:#FEF2F2;color:#7F1D1D;border:1px solid #FECACA;border-radius:8px;padding:10px}

  .empty-state{padding:15px;border:1px dashed var(--line-strong);border-radius:9px;background:var(--surface-soft);color:var(--muted);font-size:13px;text-align:center}
  .report-footer{padding:24px 4px 4px;color:var(--muted);font-size:12px;text-align:center}.report-footer strong{color:var(--ink)}
  .sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}

  @@media(max-width:900px){
    .report-page{width:min(100% - 24px,1200px)}
    .span-4,.span-5,.span-6,.span-7,.span-8{grid-column:span 12}
    .hero-grid,.chart-layout,.detail-grid{grid-template-columns:1fr}
    .overall-panel{max-width:340px}
    .control-secondary{grid-template-columns:repeat(2,minmax(0,1fr))}
    .coverage-row{grid-template-columns:1fr minmax(180px,38%)}
  }
  @@media(max-width:620px){
    body{font-size:14px}.report-page{width:min(100% - 16px,1200px);padding-top:16px}.hero{padding:22px 20px;border-radius:16px}.hero h1{font-size:29px}.card-pad{padding:18px}.bento-grid{gap:10px;margin-top:10px}.metric-card{min-height:0}.control-dock{position:static}.control-primary,.control-secondary{grid-template-columns:1fr}.failed-chip,.filter-button{justify-content:center}.context-table th{width:38%}.coverage-row{grid-template-columns:1fr;gap:9px}.coverage-rate strong{text-align:left}.radial-chart{width:190px;height:190px}.radial-chart:before{inset:24px}.scenario-table{min-width:0}.scenario-table thead{display:none}.scenario-table,.scenario-table tbody,.scenario-table tr,.scenario-table td{display:block;width:100%}.scenario-table tr[data-scenario-row]{padding:10px 0;border-bottom:1px solid var(--line)}.scenario-table td{display:grid;grid-template-columns:105px minmax(0,1fr);gap:10px;border:0!important;padding:6px 12px}.scenario-table td:before{content:attr(data-label);font-size:10px;text-transform:uppercase;letter-spacing:.05em;color:var(--muted);font-weight:800}.scenario-table td:last-child{display:block;text-align:right}.detail-row td{display:block!important}.detail-row td:before{display:none}.detail-panel{border-left:0;border-top:3px solid var(--teal)}
  }
</style>
