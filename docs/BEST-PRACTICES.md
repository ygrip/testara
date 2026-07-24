# Testara Best Practices

The approach that keeps Testara suites portable, readable, and maintainable. These are the same
rules the Testara Agent's `GenerationGuard` enforces when it generates tests — following them by
hand produces the same "flavor".

For structure see **[ARCHITECTURE.md](ARCHITECTURE.md)**; for setup see
**[GETTING-STARTED.md](GETTING-STARTED.md)**.

---

## 1. Never hardcode environment-specific values

Any value that changes between dev/staging/prod — URLs, credentials, topic names, DB names, test
data — goes through `properties(key)`.

```gherkin
# ✗ Bad — breaks the moment you switch environment
When user process request to "https://staging.example.com/api/orders"

# ✓ Good — the URL lives in config; the feature is environment-agnostic
When user process request to "order/create-order"   # spec references properties(order-api.host)
```

This is the single most important rule. It is why the same feature file runs everywhere.

## 2. Prefer built-in ("flavor") steps over custom glue

Testara ships hundreds of built-in steps across API, UI, DB, streaming, Elasticsearch, and common
validation/data/file operations. Reach for them before writing a new `@When`. A high **Flavor
Score** (share of steps that are built-in) means less glue to maintain and more readable features.

Write custom steps only for genuinely domain-specific actions — and when you do, keep them thin
and delegate to the same facades (`TestApi`, `TestUI`, `DataHolder`) the built-ins use.

## 3. API: use request-spec JSON for anything with a payload or params

Inline steps are fine for a bare GET. For requests with a body, query/path/form params, or
headers, put them in a `.json` request spec. It keeps features readable and the request reusable.

```json
{
  "specification": "order-api",
  "httpMethod": "POST",
  "url": "/orders",
  "payload": { "sku": "properties(test.sku)", "qty": 2 }
}
```

Configure the service **before** writing the feature: `api.service.<name>.*` must exist so the
spec's `specification` field resolves.

## 4. UI: pages, actions, and the URL-in-properties rule

- **Page URL belongs in `web.page.<device>.<name>.url`**, not in `@Page(url=...)`. Leave the
  annotation's `url` empty (it defaults to `""`).
- **Use a `UserAction` when a flow has 3+ operations on the same page.** Don't repeat
  low-level `click`/`enter` steps across features — encapsulate them as an `@Action` task scoped
  with `@OnPage`.
- **Assertions must be able to fail.** Use `SeeThat` / `should see … is displayed`. Assertions
  surface as `AssertionError` and are meant to fail the scenario — never wrap a verification in a
  best-effort "wait until" and treat a timeout as success.

## 5. Keep state in `DataHolder`, not in fields

Pass data between steps with `response(path)` / `request(path)` / the `assign … to <name>` steps,
which read/write the scenario-scoped `DataHolder`. Avoid stashing "last response" in your own
static or instance fields — it defeats the per-scenario isolation and breaks under parallel runs.

## 6. Respect scenario scope and parallelism

- Custom components should be `@TestComponent(scope = RegistryScope.TEST)` unless they are truly
  global immutable config (`GLOBAL`).
- Never share mutable state across scenarios through statics. The framework runs scenarios in
  parallel on separate threads; `TEST` scope + `DataHolder` are your isolation guarantees.

## 7. Fail loudly; don't swallow

When writing custom steps or command/validation logic, let real errors propagate. A step that
catches `Exception` and logs a warning turns a genuine failure into a false pass — the worst
outcome for a test framework. Reserve broad catches for genuinely optional/best-effort operations
and even then log the cause.

## 8. Generate config before features; compile before you trust

Follow the runtime chain
(`properties → commands → config → base-steps → request-specs → pages/actions → screenplay → compile`):
create the service/page config first, then the request specs / page objects, then the feature.
Run `mvn test-compile` before assuming generated Java is valid.

## 9. Tag deliberately

Tags are how you select subsets (`@smoke`, `@api`, `@regression`). Keep a small, consistent tag
vocabulary; the agent's `test-run` and Maven's `-Dcucumber.filter.tags` both rely on it.

## 10. Use the agent's guardrails as a checklist

Even without the agent, its guard rules are a good pre-commit checklist:

1. `properties(key)` for every env-specific value.
2. Request spec JSON for API requests with payload/params.
3. `UserAction` for UI flows with 3+ operations on the same page.
4. Page URL in properties, not hardcoded in `@Page`.
5. `io.github.ygrip.testara` present in scan/glue locations.
6. Service/page config generated before feature files.
7. Compile gate (`mvn test-compile`) passes.

---

## Anti-patterns quick reference

| Anti-pattern | Do instead |
|---|---|
| Hardcoded URL/credential in a step | `properties(key)` |
| Inline API request with a big payload | request-spec `.json` |
| `@Page(url = "https://…")` | `web.page.<device>.<name>.url` property |
| Repeating click/enter steps per feature | `UserAction` `@Action` task |
| "Last response" in a static/instance field | `DataHolder` via `response()`/`assign … to` |
| `catch (Exception e) { log.warn(...) }` in a step | let it throw; fail the scenario |
| Mutable statics shared across scenarios | `RegistryScope.TEST` component |
