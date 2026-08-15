<style>
  [data-report-page][hidden]{display:none!important}
  .report-navigation{position:sticky;top:8px;z-index:30;margin:0 0 14px;padding:7px;border:1px solid var(--line);border-radius:13px;background:rgba(255,255,255,.94);box-shadow:0 8px 22px rgba(22,44,64,.08);-webkit-backdrop-filter:blur(10px);backdrop-filter:blur(10px)}
  .report-navigation-track{display:flex;gap:5px;overflow-x:auto;scrollbar-width:none}.report-navigation-track::-webkit-scrollbar{display:none}
  .report-nav-link{flex:0 0 auto;padding:8px 11px;border-radius:8px;color:#526171;font-size:12px;font-weight:750;text-decoration:none;white-space:nowrap;transition:.15s ease}
  .report-nav-link:hover{background:#F2F7F8;color:var(--teal-dark)}.report-nav-link.active{background:var(--teal-soft);color:var(--teal-dark);box-shadow:inset 0 0 0 1px #BFEAE6}
  .report-view{margin-top:14px;min-width:0}.report-view>.hero{margin-bottom:14px}.report-section-card{min-height:min(620px,calc(100vh - 130px))}
  .report-section-scroll{max-height:calc(100vh - 260px);overflow:auto;overscroll-behavior:contain;scrollbar-gutter:stable;padding-right:4px}
  .additional-info-scroll{max-height:calc(100vh - 245px);overflow:auto;overscroll-behavior:contain;scrollbar-gutter:stable}
  .additional-info-scroll .context-table th{position:sticky;left:0;z-index:1}

  .hotspot-tabs{display:inline-flex;gap:4px;padding:4px;margin-bottom:15px;border:1px solid var(--line);border-radius:10px;background:#F5F8FA}
  .hotspot-tab{border:0;border-radius:7px;padding:8px 12px;background:transparent;color:var(--muted);font-size:12px;font-weight:760}
  .hotspot-tab:hover{color:var(--teal-dark)}.hotspot-tab.active{background:#FFF;color:var(--teal-dark);box-shadow:0 2px 7px rgba(15,23,42,.08)}
  .hotspot-panel[hidden]{display:none!important}

  [data-report-page="scenarios"] .results-card{min-height:calc(100vh - 132px)}
  [data-report-page="scenarios"] .scenario-table-shell{max-height:calc(100vh - 335px);overflow:auto!important;overscroll-behavior:contain;scrollbar-gutter:stable}
  [data-report-page="scenarios"] .scenario-table thead th{position:sticky;top:0;z-index:3;background:#F8FBFC}
  [data-report-page="scenarios"] .scenario-detail-panel{max-height:calc(100vh - 215px);overflow:auto;overscroll-behavior:contain;scrollbar-gutter:stable;padding-right:5px}
  .scenario-detail-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-top:11px}
  .detail-info-button{display:inline-flex;align-items:center;gap:7px;height:34px;padding:0 10px;border:1px solid #C9D8DE;border-radius:8px;background:#FFF;color:#425466;font-size:11px;font-weight:760}.detail-info-button:hover{border-color:#9EDCD7;color:var(--teal-dark);background:#F5FEFD}

  .scenario-hooks-disclosure{margin:16px 0;border:1px solid #D8E4E8;border-radius:11px;background:#FBFDFE;overflow:hidden}
  .scenario-hooks-summary{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:11px 13px;cursor:pointer;list-style:none}.scenario-hooks-summary::-webkit-details-marker{display:none}
  .scenario-hooks-copy strong{display:block;font-size:12px}.scenario-hooks-copy small{display:block;margin-top:2px;color:var(--muted);font-size:10px}.disclosure-chevron{flex:0 0 auto;color:#617281;transition:transform .16s ease}.scenario-hooks-disclosure[open] .disclosure-chevron{transform:rotate(90deg)}
  .scenario-hooks-body{padding:0 12px 12px;border-top:1px solid #E5ECEF}.scenario-hooks-body .hook-section:first-child{margin-top:12px}
  .hook-attachment-artifact{border-top:1px dashed #D7E1E7;margin-top:10px;padding-top:10px}

  .error-summary{justify-content:flex-start}.error-chevron{margin-left:auto;flex:0 0 auto;transition:transform .16s ease}.error-disclosure[open] .error-chevron{transform:rotate(90deg)}

  .attachment-button{width:100%;padding:0;border:0;text-align:left;cursor:pointer;color:inherit;font:inherit}.attachment-button:focus-visible{outline:3px solid rgba(0,175,165,.2);outline-offset:2px}
  .attachment-button .attachment-image{display:block;width:100%;height:170px;object-fit:cover;background:#EEF4F6}.attachment-button figcaption,.attachment-button .attachment-caption{display:block;padding:8px 10px;color:var(--muted);font-size:11px}
  .video-attachment-preview{height:154px;display:grid;place-items:center;background:linear-gradient(145deg,#0F172A,#193247);color:#FFF}.video-play-icon{width:46px;height:46px;display:grid;place-items:center;border:1px solid rgba(255,255,255,.22);border-radius:999px;background:rgba(255,255,255,.12)}

  .report-dialog{width:min(920px,calc(100vw - 28px));max-height:calc(100vh - 28px);padding:0;border:0;border-radius:16px;background:#FFF;color:var(--ink);box-shadow:0 28px 80px rgba(15,23,42,.3);overflow:hidden}.report-dialog::backdrop{background:rgba(8,18,31,.64);backdrop-filter:blur(3px)}
  .dialog-shell,.media-dialog-shell{display:flex;flex-direction:column;max-height:calc(100vh - 28px);min-height:0}.dialog-header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;padding:16px 18px;border-bottom:1px solid var(--line);background:#FBFDFE}.dialog-header h3{font-size:19px;margin-top:2px}.dialog-header p{margin-top:3px;color:var(--muted);font-size:11px}.icon-button{width:36px;height:36px;display:grid;place-items:center;flex:0 0 36px;border:1px solid var(--line);border-radius:9px;background:#FFF;color:#536575}.icon-button:hover{border-color:#A6DCD8;color:var(--teal-dark)}
  .dialog-scroll{overflow:auto;overscroll-behavior:contain;scrollbar-gutter:stable;padding:16px 18px}.dialog-context{display:grid;gap:2px;padding:10px 12px;margin-bottom:12px;border-left:3px solid var(--teal);background:var(--teal-soft);font-size:12px}.dialog-context span{color:#4D6870;font-size:10px}
  .step-hooks-groups{display:grid;gap:10px;counter-reset:step-hook-group}.step-hook-group{counter-increment:step-hook-group;padding:12px;border:1px solid var(--line);border-radius:11px;background:#FFF}.step-hook-group-title{display:grid;grid-template-columns:28px minmax(0,1fr);gap:10px;align-items:start}.step-hook-index{width:28px;height:28px;display:grid;place-items:center;border-radius:999px;background:var(--teal-soft);border:1px solid #B9E2DE;color:var(--teal-dark);font-size:10px;font-weight:800}.step-hook-index:before{content:counter(step-hook-group)}.step-hook-group-title strong{display:block;font-size:12px}.step-hook-group-title small{display:block;margin-top:2px;color:var(--muted);font:10px/1.45 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;overflow-wrap:anywhere}.compact-empty{margin-top:9px;padding:10px!important;font-size:11px!important}

  .media-viewer{width:min(1100px,calc(100vw - 24px))}.media-dialog-header{padding:13px 15px}.media-toolbar{display:flex;align-items:center;justify-content:center;gap:5px;padding:8px 10px;border-bottom:1px solid var(--line);background:#F8FBFC}.media-tool{min-width:34px;height:34px;display:grid;place-items:center;border:1px solid var(--line);border-radius:8px;background:#FFF;color:#485B6B;font-weight:800}.media-tool:hover:not(:disabled){border-color:#A6DCD8;color:var(--teal-dark)}.media-tool:disabled{opacity:.4;cursor:not-allowed}.media-tool.text-tool{padding:0 10px;font-size:11px}.media-tool-divider{width:1px;height:22px;background:var(--line);margin:0 3px}
  .image-viewer,.video-viewer{min-height:0;overflow:hidden}.image-stage{height:min(72vh,720px);overflow:auto;display:flex;align-items:flex-start;justify-content:flex-start;padding:14px;background:#111827;overscroll-behavior:contain}.image-stage img{display:block;max-width:none;height:auto;transform-origin:top left;transition:transform .08s ease}.image-stage img.fit{max-width:100%;max-height:100%;width:auto;height:auto;margin:auto;object-fit:contain}
  .video-stage{height:min(72vh,720px);display:grid;place-items:center;padding:14px;background:#0B1220}.video-stage video{display:block;max-width:100%;max-height:100%;width:auto;height:auto;background:#000}.video-toolbar{border-top:1px solid var(--line);border-bottom:0}

  @media(max-width:760px){
    .report-navigation{top:5px;margin-bottom:9px}.report-nav-link{padding:7px 9px;font-size:11px}.report-view{margin-top:9px}.report-section-card{min-height:calc(100vh - 105px);padding:17px 15px}
    .report-section-scroll{max-height:calc(100vh - 225px)}.additional-info-scroll{max-height:calc(100vh - 210px)}
    [data-report-page="scenarios"] .results-card{min-height:calc(100vh - 110px)}
    [data-report-page="scenarios"] .scenario-table-shell{max-height:calc(100vh - 315px)}
    [data-report-page="scenarios"] .scenario-detail-panel{max-height:calc(100vh - 185px)}
    .hotspot-tabs{display:flex;overflow-x:auto}.hotspot-tab{flex:1 0 auto}
    .report-dialog{width:calc(100vw - 14px);max-height:calc(100vh - 14px);border-radius:13px}.dialog-shell,.media-dialog-shell{max-height:calc(100vh - 14px)}
    .dialog-header{padding:13px 14px}.dialog-scroll{padding:12px}.image-stage,.video-stage{height:68vh;padding:8px}
  }

  @media(prefers-reduced-motion:reduce){.report-nav-link,.disclosure-chevron,.error-chevron,.image-stage img{transition:none!important}}
</style>
