@param String label
@param String message
@if(message != null && !message.isBlank())
<details class="error-disclosure" data-error-container open>
  <summary class="error-summary"><span>${label}</span><span class="error-toggle-label" aria-hidden="true"></span></summary>
  <pre class="error-box error-scroll" data-error-content>${message}</pre>
</details>
@endif
