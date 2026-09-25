# Changelog

All notable changes to Testara are documented in this file.

## [2.2.1] - 2026-09-26

### Added

- Added Gradle project support to `test-run`/`testara_run`: Cucumber tag and rerun filters are
  forwarded through an agent-owned, regenerated-per-run init script
  (`.testara-agent/gradle/testara-cucumber.init.gradle`), with `--gradle-task`/`run.gradleTask` to
  override the default `test` task.
- Added `testara-guide`, `testara-context`, `testara-property`, `testara-api`, `testara-ui`, and
  `testara-db` CLI subcommands alongside their existing MCP tools.
- Added `overwrite` and `compile` options to `test-plan`/`testara_plan`, `testara-ui`/`testara_ui`,
  and `testara_bootstrap`: existing files are reported as `exists: <path> (pass overwrite=true to
  replace)` instead of silently replaced, and `compile` runs the `mvn test-compile` gate after writing.

### Changed

- `test-run`/`testara_run` now runs Maven and Gradle through a shared, argv-only process runner that
  kills the whole process tree on timeout or interruption, and only trusts a `cucumber.json`/JUnit XML
  report written by the current run — an older report on disk is reported as `report missing` rather
  than silently reused. Before/after hooks, background steps, and ambiguous/undefined/pending steps now
  fail the scenario verdict.
- `test-plan`/`testara_plan` generates features from the project's real, typed built-in steps and
  blocks the write (`write blocked: ...`) when a generated step does not link to an actual step
  definition. A successful write also idempotently adds the request specs, `api.service.*` service
  config (including `automation.config.script-folder=/src/test/resources/`), and application property
  values the plan references.
- `testara-api`/`testara_api` and `testara-db`/`testara_db` config generation is idempotent and uses
  the real Elasticsearch and Kafka property keys the runtime reads; generated validation and command
  names default to the project's own package instead of leaking framework internals.
- Archetypes (`testara-archetype-api-cucumber`, `-ui-cucumber`, `-all-cucumber`) now generate samples
  that use real Testara steps and config instead of placeholder glue.
- `testara-agent.yaml` is now parsed with Jackson YAML; a checked-in file can only ever turn writes
  *off* (`write.enabled: false`) — `write`, `overwrite`, and `createFiles` are call-only keys and are
  ignored (with a warning) if present in the file.

### Fixed

- Fixed the MCP server's JSON-RPC handling (malformed requests, notifications without an `id`, unknown
  tools, `ping`) and made `TESTARA_AGENT_WRITE_ENABLED=false` / `write.enabled: false` a hard off switch
  that no per-call `write`/`overwrite`/`createFiles` argument or CLI flag can override.
- Fixed `test-run`/`test-init`/`test-plan` CLI exit codes (`0` passed/plan-only, `1` failed/timed
  out/preflight-zero-scenarios, `2` invalid input or blocked), project-relative target resolution, and
  made file writes explicit-only (no implicit APPLY mode).
- Fixed generated request specs to resolve from the script folder the runtime actually reads
  (`automation.config.script-folder=/src/test/resources/`), and added a warning when a project's own
  properties override it to a different folder.
- Fixed LLM configuration so an API key read from `TESTARA_AGENT_API_KEY` is never sent to an endpoint
  chosen by a checked-in `testara-agent.yaml`, and so `local`/`ollama` providers default to a model
  (`llama3.1`) and endpoint (`http://localhost:11434`) that actually exist on an Ollama server.
- Fixed project path containment (`ProjectPathGuard`) and secret redaction (`SecretRedactionGuard`) so
  generated-file targets stay inside the project root and tool output never echoes property values.
- Fixed knowledge-cache indexing: command/validation/step scan locations now match the runtime's own
  resolution, Rule backgrounds are included in `test-review`/`test-summary`/knowledge queries, tag
  expressions (including `except`/`or` groups) are evaluated with Cucumber's own parser, and a cache
  schema-version bump now triggers an automatic full reindex instead of serving a stale profile.
- Fixed feature-file parse errors to surface in `test-overview`/`testara-context` output instead of
  being silently dropped from the index.

## [2.1.0] - 2026-08-06

### Added

- Added the Vibium UI engine, including browser lifecycle, locator conversion, page discovery, interaction, observation, assertion, wait, mobile-emulation, and network capabilities.
- Added Vibium support to the BOM, archetypes, agent project initialization, UI catalog, and engine-parity validation.
- Added automatic Java base-package inference for agent-generated projects.
- Added thread-context propagation for virtual-thread executors so UI driver, actor, page, and test state remain available during parallel work.
- Added regression coverage for scanner caching, runtime context visibility, dependency injection, Cucumber/JUnit scope lifecycles, deferred reruns, step notifications, UI session isolation, wait capabilities, agent indexing, report parsing, command safety, and cross-platform paths.

### Changed

- Migrated the Elasticsearch integration to the Elasticsearch 8 Java client and aligned its test environment.
- Hardened the build for Java 21 and removed compiler, Javadoc, and dependency-analysis warnings across the reactor.
- Hardened GitHub Actions for Ubuntu 24.04 browser tests and migrated official workflow actions to Node.js 24-compatible releases.
- Scoped command execution caches to the active scenario and bounded the global parse cache.
- Kept UI driver and test context alive for the complete Cucumber run, with configurable driver reset behavior between scenarios.

### Fixed

- Fixed class-scanner cache-key collisions, initialization races, and cross-thread run-context visibility.
- Fixed Cucumber and JUnit lifecycle races, sequential scope teardown, deferred rerun recovery, and step notification ordering.
- Fixed current-page tracking, named-page lookup, URL wait timeouts, locator-prefix parsing, actor inheritance, null session handling, current-step timing, and driver teardown.
- Fixed API basic-auth propagation and made malformed multipart data fail with a useful error.
- Fixed agent Maven invocation and rerun handling, enforced execution safety guards, eliminated duplicate module indexing, and corrected Windows path, report parsing, JSON fingerprint, and file-content fingerprint behavior.
- Fixed packaged agent JARs and native images reporting an `unknown` version.
- Fixed validation batches silently succeeding when custom validation logic throws an `AssertionError`.

[2.2.1]: https://github.com/ygrip/testara/compare/v2.1.0...v2.2.1
[2.1.0]: https://github.com/ygrip/testara/compare/v2.0.7...v2.1.0
