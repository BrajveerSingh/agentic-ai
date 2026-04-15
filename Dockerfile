FROM eclipse-temurin:25-jre-alpine

LABEL maintainer="AI Platform Team <ai-platform@bank.internal>"
LABEL version="1.0.0"
LABEL description="Audit Trail Agent - Bank-Grade Loan Evaluation AI"
LABEL java.version="25 LTS"

# Create non-root user
RUN addgroup -g 1000 ata && \
    adduser -u 1000 -G ata -s /bin/sh -D ata

WORKDIR /app

# Copy application JAR
COPY ata-app/target/ata-app-*.jar app.jar

# Set ownership
RUN chown -R ata:ata /app

# Switch to non-root user
USER ata

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

# Expose ports
EXPOSE 8080 3000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

