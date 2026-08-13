<style>
  :root{--violet:#8B5CF6;--violet-soft:#F3EEFF;--cyan:#38BDF8;--cyan-soft:#ECF8FF;--theme-glass:rgba(255,255,255,.86)}
  body{background:radial-gradient(circle at 7% 0,rgba(0,175,165,.14),transparent 27%),radial-gradient(circle at 94% 2%,rgba(139,92,246,.12),transparent 25%),linear-gradient(180deg,#F1FAFA,#F7F8FC 360px,#F5F8FB)}
  .glass-card{background:var(--theme-glass);box-shadow:0 10px 30px rgba(22,44,64,.07),inset 0 1px 0 rgba(255,255,255,.72)}
  .glass-card:before{background:linear-gradient(90deg,var(--teal),var(--cyan) 48%,rgba(139,92,246,.7) 74%,transparent 94%)}
  .hero{background:radial-gradient(circle at 92% 2%,rgba(139,92,246,.24),transparent 31%),radial-gradient(circle at 70% 118%,rgba(0,175,165,.25),transparent 38%),linear-gradient(118deg,#091727,#10283A 58%,#173247)}
  .hero:before{background:linear-gradient(180deg,var(--teal),var(--cyan) 64%,var(--violet))}
  .metric-card.accent-cyan .icon-tile{color:#0369A1;background:var(--cyan-soft);border-color:#BAE6FD}
  .metric-card.accent-violet .icon-tile{color:#6D28D9;background:var(--violet-soft);border-color:#DDD6FE}
  .mobile-filter-toggle,.mobile-sort-strip{display:none}
</style>