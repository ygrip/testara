# MitmProxy Advanced Utility – Architecture & Implementation Plan

## Objective

Design a production-grade MitmProxy utility that supports:

* Dynamic rule control via REST API
* Thread-safe rule reload
* CI-safe startup and readiness checks
* Automatic CA certificate export
* Multi-instance parallel proxy grid
* Java-friendly control bridge

The system must be deterministic, testable, and CI-compatible.

---

# 1. High-Level Architecture

```
+------------------------------------------------------+
|                  Control Plane (API)                 |
|  - REST API (FastAPI)                                |
|  - Rule Registry (Thread-safe)                       |
|  - Instance Manager                                  |
+--------------------------+---------------------------+
                           |
                           v
+------------------------------------------------------+
|               MitmProxy Runtime Layer                |
|  - mitmdump instances                                |
|  - Dynamic script loader                             |
|  - Flow interceptor engine                           |
+--------------------------+---------------------------+
                           |
                           v
+------------------------------------------------------+
|                 Java Client Bridge                   |
|  - HTTP client wrapper                               |
|  - Rule DSL builder                                  |
|  - Instance lifecycle control                        |
+------------------------------------------------------+
```

---

# 2. Dynamic Rule Control via REST API

## Goal

Allow runtime creation, modification, and deletion of traffic rules without restarting MitmProxy.

## Design

### 2.1 Rule Model

```json
{
  "id": "rule-123",
  "priority": 10,
  "match": {
    "urlContains": "api.example.com",
    "method": "GET"
  },
  "action": {
    "type": "MODIFY_RESPONSE",
    "bodyReplace": {
      "from": "old",
      "to": "new"
    }
  },
  "enabled": true
}
```

### 2.2 REST Endpoints

| Method | Endpoint      | Purpose         |
| ------ | ------------- | --------------- |
| POST   | /rules        | Create rule     |
| GET    | /rules        | List rules      |
| PUT    | /rules/{id}   | Update rule     |
| DELETE | /rules/{id}   | Remove rule     |
| POST   | /rules/reload | Force reload    |
| GET    | /health       | Readiness check |

### 2.3 Implementation

* FastAPI container
* Rules stored in in-memory registry
* Persist rules in mounted JSON file
* Mitm script reads rule registry dynamically

---

# 3. Thread-Safe Rule Reload

## Problem

MitmProxy event loop is async; rule mutation must not cause race conditions.

## Strategy

* Use RWLock (read/write lock)
* Request handling uses read lock
* Rule mutation uses write lock
* Atomic rule swap pattern:

```python
with write_lock:
    RULES = new_rule_set
```

Mitm event hook reads from immutable snapshot.

---

# 4. CI-Safe Startup & Readiness Check

## Problem

CI must not start tests before proxy is ready.

## Solution

### 4.1 Health Endpoint

* `/health` returns:

    * Proxy process running
    * Port bound
    * CA certificate generated
    * Rule engine loaded

### 4.2 Makefile Readiness

```
make start
make wait-ready
```

`wait-ready`:

* Poll `/health`
* Timeout configurable
* Exit non-zero if failed

### 4.3 Docker Healthcheck

Add to compose:

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
  interval: 5s
  timeout: 3s
  retries: 20
```

---

# 5. Auto-Generated CA Export

## Goal

Expose generated CA certificate for browser/device trust installation.

## Design

* Mitm CA stored in mounted volume
* On first run:

    * Export `.pem`
    * Convert to `.crt`
* Expose via:

```
/certs/rootCA.pem
```

Optional:

* Provide download endpoint `/cert`

CI usage:

* Export certificate artifact
* Inject into containerized browser

---

# 6. Multi-Instance Parallel Proxy Grid

## Objective

Run N independent proxy instances for parallel test execution.

## Design

### 6.1 Instance Manager

Each instance:

* Unique container name
* Unique ports
* Dedicated volume

Example:

```
mitmproxy-1 → 8081
mitmproxy-2 → 8082
mitmproxy-3 → 8083
```

### 6.2 Dynamic Port Allocation

Control API:

```
POST /instances
```

Returns:

```json
{
  "instanceId": "proxy-3",
  "port": 8083
}
```

### 6.3 Docker Compose Scaling Option

Alternative:

```
docker compose up --scale mitmproxy=5
```

Instance-aware routing handled by control layer.

---

# 7. Java-Friendly Control Bridge

## Objective

Enable Java test frameworks (Selenium, Playwright, RestAssured) to control proxy programmatically.

## 7.1 Java Client Module

Package suggestion:

```
com.ygrip.proxycontrol
```

### Features

* createRule()
* deleteRule()
* enableRule()
* waitUntilReady()
* createInstance()
* destroyInstance()

### Example Usage

```java
ProxyClient client = new ProxyClient("http://localhost:8090");

client.createRule(
    Rule.builder()
        .urlContains("api.example.com")
        .replaceResponse("old", "new")
        .build()
);
```

## 7.2 Thread Safety

* Client is stateless
* Uses connection pooling
* Retries with exponential backoff

---

# 8. Container Layout

```
/mitm-system
 ├── docker-compose.yml
 ├── Makefile
 ├── api/
 │   ├── main.py
 │   ├── rule_registry.py
 ├── proxy/
 │   ├── interceptor.py
 ├── certs/
 └── data/
```

---

# 9. CI Integration Strategy

## Example Flow

1. make start
2. make wait-ready
3. Inject CA into browser container
4. Run tests
5. make stop

Parallel:

* Each test worker requests dedicated proxy instance
* Destroy after test completion

---

# 10. Observability & Hardening

Recommended additions:

* Structured JSON logs
* Prometheus metrics endpoint
* Rule execution counters
* Request/response tracing toggle
* Memory guard (max flows)
* Graceful shutdown hook

---

# 11. Future Enhancements

* Rule DSL
* Scenario-based rule bundles
* HAR export endpoint
* Traffic recording & replay
* Centralized distributed proxy cluster

---

# Deliverables

Phase 1:

* REST control API
* Dynamic rule reload
* Health endpoint
* Java client

Phase 2:

* Multi-instance grid
* CI integration utilities
* Observability

Phase 3:

* Distributed proxy cluster support

---

End of Plan
