# Testara Docker GHCR Deployment Plan

## 1. Executive Summary

This document defines a plan to publish **Testara Docker images** to GitHub Container Registry (GHCR) with proper image slicing.

The Docker images should help users run Testara-based automation projects faster and more consistently without repeatedly resolving the same Maven dependencies.

The initial image slices should be:

```text
api   = lightweight API automation runtime
ui    = UI automation runtime with browser dependencies
all   = full Testara runtime for mixed automation suites
```

The images should be published only when explicitly requested through:

```text
1. specific release tags
2. manual GitHub Actions workflow dispatch
```

The Docker workflow should **not** publish images on every push to `main`.

---

## 2. Goals

```text
- Publish Testara Docker images to GHCR.
- Provide clear image slices: api, ui, all.
- Keep API image lightweight.
- Keep UI image browser-ready.
- Keep all image as full convenience image.
- Pre-warm Maven dependencies to speed up CI usage.
- Use safe, predictable image tags.
- Trigger image publishing only on specific release tags or manual dispatch.
- Avoid publishing Docker images for every normal commit.
```

---

## 3. Non-Goals

```text
- Do not build every possible module-specific image in the first phase.
- Do not include Selenium Grid inside the image.
- Do not include Appium/Android SDK in the first UI image.
- Do not publish Docker images on every push.
- Do not bake project source code or credentials into the image.
- Do not make Docker images replace Maven dependency usage.
```

---

## 4. Image Slicing Strategy

Use three initial slices:

```text
ghcr.io/ygrip/testara:latest-api
ghcr.io/ygrip/testara:latest-ui
ghcr.io/ygrip/testara:latest-all
```

Versioned tags:

```text
ghcr.io/ygrip/testara:1.1.4-api
ghcr.io/ygrip/testara:1.1.4-ui
ghcr.io/ygrip/testara:1.1.4-all
```

Release tag aliases:

```text
ghcr.io/ygrip/testara:docker-v1.1.4-api
ghcr.io/ygrip/testara:docker-v1.1.4-ui
ghcr.io/ygrip/testara:docker-v1.1.4-all
```

Optional convenience tag:

```text
ghcr.io/ygrip/testara:latest
```

Recommended policy:

```text
latest = latest-all
```

Documentation should encourage explicit slice tags:

```bash
docker pull ghcr.io/ygrip/testara:latest-api
docker pull ghcr.io/ygrip/testara:latest-ui
docker pull ghcr.io/ygrip/testara:latest-all
```

---

## 5. Slice Definition

### 5.1 API Image

Image:

```text
ghcr.io/ygrip/testara:latest-api
```

Purpose:

```text
Fast API automation execution.
```

Contains:

```text
- Java 21 JDK
- Maven
- Git
- testara-bom dependency cache
- testara-api
- testara-api-cucumber
- testara-command
- testara-validation
- testara-cucumber
- testara-junit5
- testara-reporter-plugin
```

Should not contain:

```text
- Chromium / Chrome
- Playwright browser binaries
- Selenium Grid
- Appium
- Android SDK
```

Usage:

```bash
docker run --rm \
  -v "$PWD:/workspace" \
  -w /workspace \
  ghcr.io/ygrip/testara:latest-api \
  mvn test -Dcucumber.filter.tags="@api"
```

---

### 5.2 UI Image

Image:

```text
ghcr.io/ygrip/testara:latest-ui
```

Purpose:

```text
UI automation execution with browser runtime dependencies.
```

Contains:

```text
- Java 21 JDK
- Maven
- Git
- testara-ui
- testara-ui-cucumber
- testara-ui-selenium
- testara-ui-playwright
- testara-cucumber
- testara-junit5
- browser runtime dependencies
```

Optional later:

```text
- pre-installed Chromium
- pre-installed Playwright Chromium
```

Usage:

```bash
docker run --rm \
  -v "$PWD:/workspace" \
  -w /workspace \
  -e SELENIUM_DRIVER_HEADLESS=true \
  ghcr.io/ygrip/testara:latest-ui \
  mvn test -Dcucumber.filter.tags="@ui"
```

Future split:

```text
latest-ui-selenium
latest-ui-playwright
latest-ui-appium
```

Do not split UI in the first release unless image size or usage pattern justifies it.

---

### 5.3 All Image

Image:

```text
ghcr.io/ygrip/testara:latest-all
```

Purpose:

```text
Full Testara automation runtime.
```

Contains:

```text
- Everything in api image
- Everything in ui image
- testara-database
- testara-database-cucumber
- testara-streaming
- testara-streaming-cucumber
- testara-elastic
- testara-elastic-cucumber
- testara-security
- testara-reporter-plugin
```

Usage:

```bash
docker run --rm \
  -v "$PWD:/workspace" \
  -w /workspace \
  ghcr.io/ygrip/testara:latest-all \
  mvn verify
```

---

## 6. Repository Structure

Add:

```text
docker/
├── api/
│   └── Dockerfile
├── ui/
│   └── Dockerfile
├── all/
│   └── Dockerfile
└── scripts/
    ├── warmup-api.sh
    ├── warmup-ui.sh
    └── warmup-all.sh
```

Add workflow:

```text
.github/workflows/docker-ghcr.yml
```

Add docs:

```text
docs/docker.md
```

---

## 7. Dockerfile Design

### 7.1 Base Image

Recommended base:

```text
eclipse-temurin:21-jdk-jammy
```

Reason:

```text
- Java 21 compatible
- Good Maven support
- Easier browser dependency installation for UI
- Less native dependency pain than Alpine
```

Avoid Alpine for the UI image unless there is a strong reason.

---

### 7.2 API Dockerfile

Path:

```text
docker/api/Dockerfile
```

```dockerfile
FROM eclipse-temurin:21-jdk-jammy

ARG TESTARA_VERSION=1.1.4

LABEL org.opencontainers.image.source="https://github.com/ygrip/testara"
LABEL org.opencontainers.image.description="Testara API automation runtime"
LABEL org.opencontainers.image.licenses="Apache-2.0"

ENV MAVEN_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
ENV TESTARA_SLICE="api"
ENV TESTARA_VERSION="${TESTARA_VERSION}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       ca-certificates \
       curl \
       git \
       maven \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY docker/scripts/warmup-api.sh /usr/local/bin/warmup-api.sh
RUN chmod +x /usr/local/bin/warmup-api.sh \
    && /usr/local/bin/warmup-api.sh "${TESTARA_VERSION}" \
    && rm -rf /root/.m2/repository/*/*/*/*/*.lastUpdated

CMD ["mvn", "test"]
```

---

### 7.3 UI Dockerfile

Path:

```text
docker/ui/Dockerfile
```

```dockerfile
FROM eclipse-temurin:21-jdk-jammy

ARG TESTARA_VERSION=1.1.4

LABEL org.opencontainers.image.source="https://github.com/ygrip/testara"
LABEL org.opencontainers.image.description="Testara UI automation runtime with browser dependencies"
LABEL org.opencontainers.image.licenses="Apache-2.0"

ENV MAVEN_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
ENV TESTARA_SLICE="ui"
ENV TESTARA_VERSION="${TESTARA_VERSION}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       ca-certificates \
       curl \
       git \
       maven \
       wget \
       gnupg \
       fonts-liberation \
       libasound2 \
       libatk-bridge2.0-0 \
       libatk1.0-0 \
       libcups2 \
       libdbus-1-3 \
       libdrm2 \
       libgbm1 \
       libgtk-3-0 \
       libnspr4 \
       libnss3 \
       libx11-xcb1 \
       libxcomposite1 \
       libxdamage1 \
       libxrandr2 \
       xdg-utils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY docker/scripts/warmup-ui.sh /usr/local/bin/warmup-ui.sh
RUN chmod +x /usr/local/bin/warmup-ui.sh \
    && /usr/local/bin/warmup-ui.sh "${TESTARA_VERSION}" \
    && rm -rf /root/.m2/repository/*/*/*/*/*.lastUpdated

CMD ["mvn", "test"]
```

---

### 7.4 All Dockerfile

Path:

```text
docker/all/Dockerfile
```

```dockerfile
FROM eclipse-temurin:21-jdk-jammy

ARG TESTARA_VERSION=1.1.4

LABEL org.opencontainers.image.source="https://github.com/ygrip/testara"
LABEL org.opencontainers.image.description="Full Testara automation runtime"
LABEL org.opencontainers.image.licenses="Apache-2.0"

ENV MAVEN_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
ENV TESTARA_SLICE="all"
ENV TESTARA_VERSION="${TESTARA_VERSION}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       ca-certificates \
       curl \
       git \
       maven \
       wget \
       gnupg \
       fonts-liberation \
       libasound2 \
       libatk-bridge2.0-0 \
       libatk1.0-0 \
       libcups2 \
       libdbus-1-3 \
       libdrm2 \
       libgbm1 \
       libgtk-3-0 \
       libnspr4 \
       libnss3 \
       libx11-xcb1 \
       libxcomposite1 \
       libxdamage1 \
       libxrandr2 \
       xdg-utils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY docker/scripts/warmup-all.sh /usr/local/bin/warmup-all.sh
RUN chmod +x /usr/local/bin/warmup-all.sh \
    && /usr/local/bin/warmup-all.sh "${TESTARA_VERSION}" \
    && rm -rf /root/.m2/repository/*/*/*/*/*.lastUpdated

CMD ["mvn", "verify"]
```

---

## 8. Maven Warmup Scripts

The warmup scripts create temporary Maven projects and run:

```bash
mvn -B -q dependency:go-offline
```

This pre-populates the Docker image Maven cache for the selected slice.

For the real implementation, keep the scripts small and aligned with the current Testara modules.

---

## 9. Trigger Policy

The Docker workflow must not publish on every push.

Allowed triggers:

```text
1. specific Docker release tags
2. manual workflow dispatch
```

Recommended release tag pattern:

```text
docker-v*
```

Examples:

```bash
git tag docker-v1.1.4
git push origin docker-v1.1.4
```

This avoids accidentally publishing images for every Maven release tag.

Alternative accepted patterns:

```text
v*-docker
testara-docker-v*
```

Recommended pattern:

```text
docker-v{version}
```

Reason:

```text
- explicit
- easy to understand
- independent from Maven Central release tag
- avoids accidental Docker publish
```

Manual dispatch should allow:

```text
- version input
- slice input: api/ui/all/all-slices
- push true/false
```

---

## 10. GitHub Actions Workflow

Path:

```text
.github/workflows/docker-ghcr.yml
```

```yaml
name: Publish Testara Docker Images to GHCR

on:
  push:
    tags:
      - "docker-v*.*.*"
  workflow_dispatch:
    inputs:
      testara_version:
        description: "Testara version to bake into the image, for example 1.1.4"
        required: true
        type: string
      slice:
        description: "Image slice to build"
        required: true
        default: "all"
        type: choice
        options:
          - api
          - ui
          - all
          - all-slices
      push_images:
        description: "Push images to GHCR"
        required: true
        default: "true"
        type: choice
        options:
          - "true"
          - "false"

permissions:
  contents: read
  packages: write

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ygrip/testara

jobs:
  resolve:
    name: Resolve Docker publish metadata
    runs-on: ubuntu-latest
    outputs:
      version: ${{ steps.version.outputs.version }}
      should_push: ${{ steps.flags.outputs.should_push }}
      build_api: ${{ steps.flags.outputs.build_api }}
      build_ui: ${{ steps.flags.outputs.build_ui }}
      build_all: ${{ steps.flags.outputs.build_all }}

    steps:
      - name: Resolve version
        id: version
        shell: bash
        run: |
          if [ "${{ github.event_name }}" = "workflow_dispatch" ]; then
            VERSION="${{ inputs.testara_version }}"
          else
            RAW_TAG="${GITHUB_REF_NAME}"
            VERSION="${RAW_TAG#docker-v}"
          fi

          if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.-]+)?$ ]]; then
            echo "Invalid version: $VERSION"
            exit 1
          fi

          echo "version=$VERSION" >> "$GITHUB_OUTPUT"
          echo "Resolved version: $VERSION"

      - name: Resolve slices and push flag
        id: flags
        shell: bash
        run: |
          if [ "${{ github.event_name }}" = "workflow_dispatch" ]; then
            SLICE="${{ inputs.slice }}"
            SHOULD_PUSH="${{ inputs.push_images }}"
          else
            SLICE="all-slices"
            SHOULD_PUSH="true"
          fi

          BUILD_API="false"
          BUILD_UI="false"
          BUILD_ALL="false"

          case "$SLICE" in
            api)
              BUILD_API="true"
              ;;
            ui)
              BUILD_UI="true"
              ;;
            all)
              BUILD_ALL="true"
              ;;
            all-slices)
              BUILD_API="true"
              BUILD_UI="true"
              BUILD_ALL="true"
              ;;
            *)
              echo "Unsupported slice: $SLICE"
              exit 1
              ;;
          esac

          echo "should_push=$SHOULD_PUSH" >> "$GITHUB_OUTPUT"
          echo "build_api=$BUILD_API" >> "$GITHUB_OUTPUT"
          echo "build_ui=$BUILD_UI" >> "$GITHUB_OUTPUT"
          echo "build_all=$BUILD_ALL" >> "$GITHUB_OUTPUT"

  docker:
    name: Build ${{ matrix.slice }} image
    needs: resolve
    runs-on: ubuntu-latest

    strategy:
      fail-fast: false
      matrix:
        include:
          - slice: api
            dockerfile: docker/api/Dockerfile
            description: "Testara API automation runtime"
          - slice: ui
            dockerfile: docker/ui/Dockerfile
            description: "Testara UI automation runtime"
          - slice: all
            dockerfile: docker/all/Dockerfile
            description: "Full Testara automation runtime"

    steps:
      - name: Check whether slice should build
        id: enabled
        shell: bash
        run: |
          ENABLED="false"

          if [ "${{ matrix.slice }}" = "api" ] && [ "${{ needs.resolve.outputs.build_api }}" = "true" ]; then
            ENABLED="true"
          fi

          if [ "${{ matrix.slice }}" = "ui" ] && [ "${{ needs.resolve.outputs.build_ui }}" = "true" ]; then
            ENABLED="true"
          fi

          if [ "${{ matrix.slice }}" = "all" ] && [ "${{ needs.resolve.outputs.build_all }}" = "true" ]; then
            ENABLED="true"
          fi

          echo "enabled=$ENABLED" >> "$GITHUB_OUTPUT"

      - name: Checkout
        if: steps.enabled.outputs.enabled == 'true'
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        if: steps.enabled.outputs.enabled == 'true'
        uses: docker/setup-buildx-action@v3

      - name: Login to GHCR
        if: steps.enabled.outputs.enabled == 'true' && needs.resolve.outputs.should_push == 'true'
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Docker metadata
        if: steps.enabled.outputs.enabled == 'true'
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          labels: |
            org.opencontainers.image.title=Testara ${{ matrix.slice }}
            org.opencontainers.image.description=${{ matrix.description }}
            org.opencontainers.image.source=https://github.com/ygrip/testara
            org.opencontainers.image.licenses=Apache-2.0
          tags: |
            type=raw,value=${{ needs.resolve.outputs.version }}-${{ matrix.slice }}
            type=raw,value=latest-${{ matrix.slice }}
            type=raw,value=${{ github.sha }}-${{ matrix.slice }}

      - name: Build and optionally push
        if: steps.enabled.outputs.enabled == 'true'
        uses: docker/build-push-action@v6
        with:
          context: .
          file: ${{ matrix.dockerfile }}
          push: ${{ needs.resolve.outputs.should_push == 'true' }}
          build-args: |
            TESTARA_VERSION=${{ needs.resolve.outputs.version }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha,scope=testara-${{ matrix.slice }}
          cache-to: type=gha,mode=max,scope=testara-${{ matrix.slice }}
```

---

## 11. Trigger Examples

### 11.1 Publish all slices through tag

```bash
git tag docker-v1.1.4
git push origin docker-v1.1.4
```

This builds and pushes:

```text
ghcr.io/ygrip/testara:1.1.4-api
ghcr.io/ygrip/testara:1.1.4-ui
ghcr.io/ygrip/testara:1.1.4-all
ghcr.io/ygrip/testara:latest-api
ghcr.io/ygrip/testara:latest-ui
ghcr.io/ygrip/testara:latest-all
```

### 11.2 Manual dry-run build without push

GitHub Actions → `Publish Testara Docker Images to GHCR` → Run workflow:

```text
testara_version: 1.1.4
slice: api
push_images: false
```

This validates the image build without publishing.

### 11.3 Manual publish only API image

```text
testara_version: 1.1.4
slice: api
push_images: true
```

### 11.4 Manual publish all slices

```text
testara_version: 1.1.4
slice: all-slices
push_images: true
```

---

## 12. Local Build Commands

Build API:

```bash
docker build \
  -f docker/api/Dockerfile \
  --build-arg TESTARA_VERSION=1.1.4 \
  -t ghcr.io/ygrip/testara:local-api .
```

Build UI:

```bash
docker build \
  -f docker/ui/Dockerfile \
  --build-arg TESTARA_VERSION=1.1.4 \
  -t ghcr.io/ygrip/testara:local-ui .
```

Build all:

```bash
docker build \
  -f docker/all/Dockerfile \
  --build-arg TESTARA_VERSION=1.1.4 \
  -t ghcr.io/ygrip/testara:local-all .
```

---

## 13. Local Smoke Test

Check Java and Maven:

```bash
docker run --rm ghcr.io/ygrip/testara:local-api java -version
docker run --rm ghcr.io/ygrip/testara:local-api mvn -version
```

Run against project:

```bash
docker run --rm \
  -v "$PWD:/workspace" \
  -w /workspace \
  ghcr.io/ygrip/testara:local-api \
  mvn test -Dcucumber.filter.tags="@api"
```

Run UI headless:

```bash
docker run --rm \
  -v "$PWD:/workspace" \
  -w /workspace \
  -e SELENIUM_DRIVER_HEADLESS=true \
  ghcr.io/ygrip/testara:local-ui \
  mvn test -Dcucumber.filter.tags="@ui"
```

Run all:

```bash
docker run --rm \
  -v "$PWD:/workspace" \
  -w /workspace \
  ghcr.io/ygrip/testara:local-all \
  mvn verify
```

---

## 14. GHCR Visibility

After first publish:

```text
GitHub → Packages → testara → Package settings → Change visibility → Public
```

Recommended:

```text
Testara Docker images should be public.
```

Reason:

```text
- easier client usage
- no docker login required for public pulls
- better open-source adoption
```

---

## 15. Security and Hardening

Recommended controls:

```text
- use GITHUB_TOKEN for GHCR publishing
- no secrets in Dockerfile
- no secrets in image layers
- add OCI labels
- keep API image small
- keep UI dependencies only in UI/all images
- run image scanning later
- publish only from specific tag or manual workflow
- do not publish on every push
```

Optional non-root user:

```dockerfile
RUN useradd -m -u 10001 testara
USER testara
```

If using non-root, ensure Maven cache is writable:

```dockerfile
ENV MAVEN_CONFIG=/home/testara/.m2
```

Optional Trivy scan later:

```yaml
- name: Scan image
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: ghcr.io/ygrip/testara:${{ needs.resolve.outputs.version }}-${{ matrix.slice }}
    format: table
    exit-code: "1"
    severity: CRITICAL,HIGH
```

---

## 16. CI Usage In Client Project

API tests:

```yaml
name: Testara API Tests

on:
  push:
  pull_request:

jobs:
  api-tests:
    runs-on: ubuntu-latest
    container:
      image: ghcr.io/ygrip/testara:latest-api

    steps:
      - uses: actions/checkout@v4

      - name: Run API tests
        run: mvn test -Dcucumber.filter.tags="@api"
```

UI tests:

```yaml
name: Testara UI Tests

on:
  push:
  pull_request:

jobs:
  ui-tests:
    runs-on: ubuntu-latest
    container:
      image: ghcr.io/ygrip/testara:latest-ui

    steps:
      - uses: actions/checkout@v4

      - name: Run UI tests
        run: mvn test -Dcucumber.filter.tags="@ui"
```

Mixed tests:

```yaml
name: Testara Full Tests

on:
  push:
  pull_request:

jobs:
  full-tests:
    runs-on: ubuntu-latest
    container:
      image: ghcr.io/ygrip/testara:latest-all

    steps:
      - uses: actions/checkout@v4

      - name: Run tests
        run: mvn verify
```

---

## 17. Future Slicing Roadmap

Phase 1:

```text
api
ui
all
```

Phase 2:

```text
ui-selenium
ui-playwright
ui-appium
```

Phase 3:

```text
database
streaming
elastic
security
agent
mcp
```

Possible future catalog:

```text
ghcr.io/ygrip/testara:latest-api
ghcr.io/ygrip/testara:latest-ui
ghcr.io/ygrip/testara:latest-all
ghcr.io/ygrip/testara:latest-agent
ghcr.io/ygrip/testara:latest-mcp
```

---

## 18. Acceptance Criteria

The implementation is complete when:

```text
- api image builds locally
- ui image builds locally
- all image builds locally
- Docker workflow does not run on normal push to main
- Docker workflow runs on docker-v*.*.* tag
- Docker workflow can be manually dispatched
- manual dispatch can build api, ui, all, or all-slices
- manual dispatch can build without push
- GHCR push works
- images have OCI source/description/license labels
- package visibility is public
- latest-* tags are published
- versioned slice tags are published
- sample project can run API tests with latest-api
- sample project can run UI tests with latest-ui
- sample project can run mixed tests with latest-all
```

---

## 19. Recommended First Implementation

Implement in this order:

```text
1. Add docker/api/Dockerfile.
2. Add docker/ui/Dockerfile.
3. Add docker/all/Dockerfile.
4. Add warmup scripts.
5. Build all images locally.
6. Add docker-ghcr.yml with only manual dispatch first.
7. Test manual dispatch with push_images=false.
8. Test manual dispatch with push_images=true for api only.
9. Add docker-v*.*.* tag trigger.
10. Publish all slices using docker-v1.1.4.
11. Make GHCR package public.
12. Add docs/docker.md.
```

Recommended release command:

```bash
git tag docker-v1.1.4
git push origin docker-v1.1.4
```

This keeps Docker publishing deliberate, controlled, and separate from ordinary code pushes.
