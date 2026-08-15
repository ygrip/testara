# Testara Testcontainers

`testara-testcontainers` provides a small, framework-owned lifecycle abstraction for Testcontainers resources without coupling Testara Core to Docker.

## Dependency

```xml
<dependency>
  <groupId>io.github.ygrip</groupId>
  <artifactId>testara-testcontainers</artifactId>
  <version>${testara.version}</version>
  <scope>test</scope>
</dependency>
```

## Usage

Keep one `ManagedTestContainer` in a run-scoped owner such as a Testara `GLOBAL` component, a static test fixture, or a custom `TestaraExtension`.

```java
private static final ManagedTestContainer<GenericContainer<?>> GRID =
    ManagedTestContainer.of(
        "mitmproxy-grid",
        () -> new GenericContainer<>(
            DockerImageName.parse(MitmProxyGridCompatibility.SUPPORTED_IMAGE)
        )
            .withExposedPorts(8090)
            .waitingFor(Wait.forHttp("/health").forPort(8090))
    );

GenericContainer<?> container = GRID.getOrStart();
String apiUrl = "http://%s:%d".formatted(
    container.getHost(),
    container.getMappedPort(8090)
);
```

`getOrStart()` is thread-safe and starts only one resource for callers sharing the same wrapper. Testara automatically calls `stop()` at end-of-run through `ResourceShutdownRegistry`.

Manual shutdown is also safe and idempotent:

```java
GRID.stop();
```

Calling `getOrStart()` after `stop()` creates a fresh resource.

## Reuse semantics

"Managed" means shared inside the current JVM/test run. This module intentionally does **not** enable Testcontainers' cross-process reusable-container mode (`withReuse(true)`), keeping CI/Jenkins runs isolated from each other.

## Why a separate module?

Testcontainers is an optional automation concern. Keeping it in `testara-testcontainers` avoids making Docker/Testcontainers a transitive dependency of every Testara user while still giving automation projects a common lifecycle abstraction.
