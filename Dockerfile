# ==========================================
# Stage 1: Build Stage
# ==========================================
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy Maven descriptor and dependency files first for layer caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN mvn dependency:go-offline -B || true

# Copy source code and package application
COPY src src
RUN mvn clean package -DskipTests

# ==========================================
# Stage 2: Lightweight Runtime Stage
# ==========================================
FROM eclipse-temurin:21-jre-jammy

# Create non-root user for security
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -s /bin/sh -m appuser

WORKDIR /app

# Copy executable jar from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar /app/app.jar

# Install curl for container health check
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

USER appuser

EXPOSE 8080

# Configure container healthcheck via Actuator
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
