FROM nginx:alpine

# Configurable ports via environment variables
ENV UI_PORT=8088
ENV ADMIN_PORT=8081

# nginx template with envsubst for variable substitution
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY static/ /usr/share/nginx/html/

EXPOSE ${UI_PORT}
