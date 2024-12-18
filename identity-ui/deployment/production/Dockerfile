FROM nginx:stable-alpine

LABEL org.opencontainers.image.description="Web-based user interface for the 'stasis/identity' service"

RUN mkdir -p /opt/stasis-identity-ui/templates

COPY build/web /usr/share/nginx/html
COPY deployment/production/.env.template /opt/stasis-identity-ui/templates
COPY deployment/production/nginx.template /opt/stasis-identity-ui/templates
COPY deployment/production/entrypoint.sh /opt/stasis-identity-ui

RUN chmod +x /opt/stasis-identity-ui/entrypoint.sh

ENTRYPOINT ["/opt/stasis-identity-ui/entrypoint.sh"]
