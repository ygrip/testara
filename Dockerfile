# ── Build stage ────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY testara-agent/pom.xml testara-agent/
COPY testara-agent-cli/pom.xml testara-agent-cli/
COPY testara-agent-mcp/pom.xml testara-agent-mcp/
# Download dependencies (cache layer)
RUN mvn -pl testara-agent-cli -am dependency:go-offline -B -q || true

COPY . .
RUN mvn -pl testara-agent-cli -am package -DskipTests -B -q

# ── Runtime stage ───────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.source="https://github.com/ygrip/testara"
LABEL org.opencontainers.image.description="Testara Agent — agentic skills for Testara automation projects"

RUN addgroup -S testara && adduser -S testara -G testara
USER testara
WORKDIR /workspace

COPY --from=build /workspace/testara-agent-cli/target/testara-agent.jar /app/testara-agent.jar

ENTRYPOINT ["java", "-jar", "/app/testara-agent.jar"]
CMD ["--help"]
