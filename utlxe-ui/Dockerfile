FROM nginx:alpine

# Configurable ports via environment variables
ENV UI_PORT=8088
ENV ADMIN_PORT=8081
# EF24: the engine's data plane, and the credential that fronts it. The key defaults to EMPTY
# and the nginx template answers 503 on the data path until it is set -- an unconfigured image
# must not publish an open execution path.
ENV DATA_PORT=8085
ENV DATA_PLANE_KEY=""

# nginx template with envsubst for variable substitution
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY static/ /usr/share/nginx/html/

EXPOSE ${UI_PORT}
