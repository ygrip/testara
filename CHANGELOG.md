# Changelog

All notable changes to Testara are documented in this file.

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

[2.1.0]: https://github.com/ygrip/testara/compare/v2.0.7...v2.1.0
