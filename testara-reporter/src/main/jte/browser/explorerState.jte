<script>
(function(){
  var clear=document.querySelector('[data-inline-clear]');
  var search=document.querySelector('[data-report-search]');
  var filters=Array.from(document.querySelectorAll('[data-status-filter],[data-feature-filter],[data-suite-filter],[data-tag-filter]'));
  if(!clear||!search)return;
  function update(){clear.hidden=!(search.value.trim()!==''||filters.some(function(filter){return filter.value!=='';}));}
  search.addEventListener('input',update);
  filters.forEach(function(filter){filter.addEventListener('change',update);});
  clear.addEventListener('click',function(){window.setTimeout(update,0);});
  update();
})();
</script>
