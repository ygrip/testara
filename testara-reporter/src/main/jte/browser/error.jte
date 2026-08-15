@param String label
@param String message
@if(message != null && !message.isBlank())
<details class="error-disclosure" data-error-container open>
  <summary class="error-summary"><span>${label}</span><svg class="error-chevron" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6"></path></svg></summary>
  <pre class="error-box error-scroll" data-error-content>${message}</pre>
</details>
@endif
