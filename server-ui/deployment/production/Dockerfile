FROM nginx:stable-alpine

LABEL org.opencontainers.image.description="Web-based user interface for the 'stasis/server' service"

RUN mkdir -p /opt/stasis-server-ui/templates

COPY build/web /usr/share/nginx/html
COPY deployment/production/.env.template /opt/stasis-server-ui/templates
COPY deployment/production/nginx.template /opt/stasis-server-ui/templates
COPY deployment/production/entrypoint.sh /opt/stasis-server-ui

RUN chmod +x /opt/stasis-server-ui/entrypoint.sh

ENTRYPOINT ["/opt/stasis-server-ui/entrypoint.sh"]
