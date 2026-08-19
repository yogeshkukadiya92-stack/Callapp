FROM ghcr.io/cirruslabs/android-sdk:35 AS builder

WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew assembleDebug --no-daemon --console=plain

FROM nginx:1.27-alpine
COPY deploy/index.html /usr/share/nginx/html/index.html
COPY --from=builder /workspace/app/build/outputs/apk/debug/app-debug.apk /usr/share/nginx/html/callflow-debug.apk

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -q --spider http://127.0.0.1/ || exit 1
