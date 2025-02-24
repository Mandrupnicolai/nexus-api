# =============================================================================
# Multi-stage Dockerfile for NexusAPI
#
# Stage 1 (builder): Compiles the application using Maven inside a JDK image.
#                    The Maven cache is mounted as a Docker layer to speed up
#                    subsequent builds.
#
# Stage 2 (runtime): Copies only the compiled JAR into a minimal JRE image.
#                    The final image is ~250MB vs ~600MB for a full JDK image.
#
# Build:  docker build -t nexusapi .
# Run:    docker run -p 8080:8080 nexusapi
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1 — Build
# ---------------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy only the POM first to leverage Docker layer caching.
# Dependencies are re-downloaded only when pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and compile — skipping tests here; CI runs tests separately
COPY src ./src
RUN mvn package -B -DskipTests

# ---------------------------------------------------------------------------
# Stage 2 — Runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Create a non-root user to run the application — security best practice
RUN addgroup -S nexus && adduser -S nexus -G nexus
USER nexus

WORKDIR /app

# Copy the fat JAR from the build stage
COPY --from=builder /build/target/nexus-api-*.jar app.jar

# Expose the default port (overridable via SERVER_PORT environment variable)
EXPOSE 8080

# JVM tuning for containers:
#   -XX:+UseContainerSupport    — respects Docker memory limits (default in JDK 11+)
#   -XX:MaxRAMPercentage=75.0   — use up to 75% of container RAM for the heap
#   -XX:+ExitOnOutOfMemoryError — restart instead of limping with OOM errors
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "app.jar"]
