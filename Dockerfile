FROM ghcr.io/cirruslabs/android-sdk:35 AS builder

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /workspace
COPY . .
RUN chmod +x gradlew \
    && ./gradlew assembleDebug --no-daemon --console=plain & \
    gradle_pid=$!; \
    while kill -0 "$gradle_pid" 2>/dev/null; do \
      echo "Gradle build in progress..."; \
      sleep 30; \
    done; \
    wait "$gradle_pid"

FROM nginx:1.27-alpine
COPY deploy/index.html /usr/share/nginx/html/index.html
COPY --from=builder /workspace/app/build/outputs/apk/debug/app-debug.apk /usr/share/nginx/html/callflow-debug.apk

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -q --spider http://127.0.0.1/ || exit 1
