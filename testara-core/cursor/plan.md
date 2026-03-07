# SPI-based ObjectFactory Architecture Plan

## Goal

Design an SPI-driven ObjectFactory system that supports:

- Spring-based dependency resolution (when Spring is present)
- Custom, non-Spring dependency resolution (default factory)
- Parallel test execution
- Strong isolation (per-test, per-thread)
- Shared and singleton lifecycles
- Zero coupling between core framework and Spring
- Plug-and-play selection via SPI or configuration

---

## Core Design Principles

1. **SPI-first**  
   ObjectFactory selection must be resolved via Java SPI, not compile-time dependency.

2. **Single Resolution Path**  
   All object creation and retrieval flows through a single `ObjectFactory` interface.

3. **Spring Delegation, Not Duplication**  
   When Spring is available, object lifecycle is delegated to Spring — not duplicated.

4. **Scope-Aware, Engine-Agnostic**  
   No dependency on JUnit, Cucumber, or Serenity APIs in core modules.

5. **Deterministic Parallelism**  
   Thread-safe instance resolution with explicit scope boundaries.

---

## High-Level Architecture

            +-----------------------+
            |  Test Engine (JUnit)  |
            +-----------+-----------+
                        |
                        v
                +---------------+
                | ScopeContext  |
                +---------------+
                        |
                        v   
    +------------------+ +-------------------+ +----------------------+
    |    DefaultObject | | SpringObject      | | FutureObjectFactory |
    |       Factory    | |   Factory         | | (Guice, etc)        |
    +--------+---------+ +---------+---------+ +----------+-----------+
             |                     |                      |
             +-----------+-----------+-----------+------------+
                    |                       |
                    v                       v
                +----------------------------------+
                |         RootRegistry             |
                +----------------------------------+
                                |
                                v
                    +-------------------+
                    | ScopedProvider<T> |
                    +-------------------+


---

## SPI Contract

### ObjectFactory SPI

```java
public interface ObjectFactory {

    <T> T getInstance(Class<T> type);

    default boolean supports(Class<?> type) {
        return true;
    }

    default int priority() {
        return 0;
    }
}
```
---

## Factory Selection Strategy
### SPI Loader

- Load all ObjectFactory implementations using ServiceLoader 
- Sort by priority()
- Select first factory whose supports() returns true

### Example Priorities
| Factory              | Priority |
| -------------------- | -------- |
| SpringObjectFactory  | 100      |
| DefaultObjectFactory | 0        |

---
## RootRegistry Responsibilities

- Maintain mapping of:
  - Class<?> → ScopedProvider<?>
- Manage instance scopes:
  - GLOBAL (shared singleton)
  - THREAD (per-thread)
  - TEST (per-test)
- Provide deterministic retrieval:
- No instantiation logic
- No Spring awareness

---

## ScopedProvider Responsibilities

- Store instances by resolved scope key 
- Lazily create instances 
- Thread-safe 
- No test-engine awareness

---
## ScopeContext SPI

```java
public interface ScopeContext {
    String currentScopeKey();
}
```

### Implementation
| Context     | Source                           |
| ----------- | -------------------------------- |
| ThreadScope | `Thread.currentThread().getId()` |
| TestScope   | JUnit / Cucumber extension       |
| GlobalScope | Constant                         |

---
## DefaultObjectFactory (Non-Spring)
### Responsibilities
- Constructor-based instantiation 
- Recursive dependency resolution 
- Delegation to RootRegistry when provider exists 
- No framework dependencies
### Resolution Algorithm
1. Check RootRegistry.contains(type)
2. If yes → RootRegistry.get(type)
3. Else:
   - Select best constructor 
   - Resolve parameters recursively 
   - Instantiate 
   - Return (do NOT cache unless registered)

---

## SpringObjectFactory (Spring Module)
### Responsibilities
- Delegate instance resolution to Spring ApplicationContext 
- Never instantiate directly 
- Never register Spring-managed beans into RootRegistry

### Behavior
```java
if (applicationContext.containsBean(type)) {
    return applicationContext.getBean(type);
}
return delegate.getInstance(type); // fallback
```

### Instance Ownership Rules
| Scenario            | Owner                |
| ------------------- | -------------------- |
| Spring Bean         | Spring               |
| Registered Provider | RootRegistry         |
| Ad-hoc Utility      | DefaultObjectFactory |

No instance is ever owned by more than one system.

---
## Parallelization & Isolation
### Guarantees
- Thread-safe access via ConcurrentHashMap 
- Scoped isolation via scope keys 
- No static mutable state 
- Deterministic lifecycle

### Lifecycle Hooks (Optional)
```java
interface ScopeLifecycle {
    void onTestStart(String testId);
    void onTestEnd(String testId);
}

```

Used by:
- Test engines 
- Cleanup logic 
- Registry eviction

## Configuration & Enablement
### SPI
```bash
META-INF/services/com.example.ObjectFactory
```

## Anti-Patterns to Avoid
- Static singleton registries 
- Mixing Spring and registry lifecycles 
- Resolving instances inside contains()
- Test-engine logic in core modules

## Success Criteria
- Zero dependency footprint unless needed 
- Deterministic behavior under parallel execution 
- Clear ownership of instances 
- Seamless Spring / non-Spring coexistence 
- Extendable via SPI

---
## Next Steps
- Implement constructor resolver via `InstanceResolver`
- Add eviction / cleanup hooks 
- Integrate with JUnit 5 Extension API via `TestContextExtension`