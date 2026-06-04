# utlx-lang.org — build the VitePress static site and serve it with nginx.
#
# The chapters are pre-converted to Markdown and committed (see scripts/convert-book.sh),
# so the build needs ONLY Node — no pandoc/Typst in the image. Coolify builds this with the
# build context set to /website (Base Directory), which is where this Dockerfile lives.

# ---- Build stage: VitePress → static HTML ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build
# output → /app/.vitepress/dist

# ---- Serve stage: nginx serving the static site ----
FROM nginx:1.27-alpine AS serve
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/.vitepress/dist /usr/share/nginx/html
EXPOSE 80
# (nginx's default CMD runs the server)
