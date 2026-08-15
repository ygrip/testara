<style data-ui-refinement="navigation-accessibility-contrast">
  :root{
    --nav-control-size:44px;
    --chart-voice-bg:#E8F8F6;
    --chart-voice-text:#173F45;
    --chart-voice-strong:#006A65;
  }

  .report-navigation{padding:10px;border-radius:22px;gap:10px}
  .report-navigation-track{gap:6px}
  .report-nav-link{min-height:var(--nav-control-size);padding:0 14px;border-radius:14px;font-size:12px;line-height:1.2}
  .report-nav-icon{width:18px;height:18px;flex-basis:18px}
  .report-theme-toggle{min-height:var(--nav-control-size);padding:0 15px;border-radius:999px;font-size:12px;line-height:1.2}
  .theme-toggle-icon{width:18px;height:18px}
  .report-nav-link:focus-visible,.report-theme-toggle:focus-visible{outline:3px solid var(--teal);outline-offset:2px}

  .chart-voice{background:var(--chart-voice-bg);color:var(--chart-voice-text);border-left-color:var(--teal)}
  .chart-voice strong{color:var(--chart-voice-strong)}

  html[data-report-theme="dark"]{
    --chart-voice-bg:#12383D;
    --chart-voice-text:#D9ECEF;
    --chart-voice-strong:#A7F3ED;
  }
  html[data-report-theme="dark"] .danger-eyebrow,
  html[data-report-theme="dark"] .danger-value,
  html[data-report-theme="dark"] .step-error{color:#FCA5A5}
  html[data-report-theme="dark"] .warning-eyebrow{color:#FCD34D}
  html[data-report-theme="dark"] .icon-tile.danger{color:#FCA5A5;background:#321A20;border-color:#6D3038}
  html[data-report-theme="dark"] .icon-tile.info,
  html[data-report-theme="dark"] .metric-card.accent-cyan .icon-tile{color:#BAE6FD;background:#102F45;border-color:#245B78}
  html[data-report-theme="dark"] .metric-card.accent-violet .icon-tile,
  html[data-report-theme="dark"] .example-badge{color:#E9D5FF;background:#292144;border-color:#5B4A87}
  html[data-report-theme="dark"] .failed-chip{color:#FCA5A5;background:#2A171B;border-color:#6D3038}
  html[data-report-theme="dark"] .status-passed{color:#86EFAC;background:#123220;border-color:#2F6E45}
  html[data-report-theme="dark"] .status-failed{color:#FCA5A5;background:#321A20;border-color:#6D3038}
  html[data-report-theme="dark"] .status-skipped{color:#D7E3ED;background:#1B2936;border-color:#475569}
  html[data-report-theme="dark"] .status-pending,
  html[data-report-theme="dark"] .status-undefined{color:#FCD34D;background:#332B16;border-color:#6B5A1C}
  html[data-report-theme="dark"] .error-box{color:#FECACA;background:#2A171B;border-color:#6D3038}
  html[data-report-theme="dark"] .attachment-file,
  html[data-report-theme="dark"] .detail-button{color:#A0F0EA;background:#102D31;border-color:#27545A}
  html[data-report-theme="dark"] .clean-search-shell .search-icon,
  html[data-report-theme="dark"] .inline-clear-button{color:#9BAEBE}
  html[data-report-theme="dark"] .inline-clear-button:hover{color:#A0F0EA;background:#18303D}

  @media(max-width:760px){
    .report-navigation{top:6px;padding:8px;border-radius:20px;gap:8px}
    .report-navigation-track{gap:5px;scroll-padding-inline:2px}
    .report-nav-link{min-height:var(--nav-control-size);padding:0 12px;border-radius:13px;font-size:12px}
    .report-nav-link span,.theme-toggle-label{display:inline!important}
    .report-theme-toggle{width:auto;height:var(--nav-control-size);min-height:var(--nav-control-size);padding:0 13px;border-radius:999px}
  }

  @media(max-width:420px){
    .report-navigation{grid-template-columns:minmax(0,1fr) auto}
    .report-theme-toggle{padding:0 11px}
  }
</style>
