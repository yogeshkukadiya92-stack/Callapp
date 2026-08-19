FROM nginx:1.27-alpine
COPY deploy/index.html /usr/share/nginx/html/index.html
COPY deploy/callflow-debug.apk /usr/share/nginx/html/callflow-debug.apk

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -q --spider http://127.0.0.1/ || exit 1
