# syntax=docker/dockerfile:1

# ---- deps: full install (client libs needed for the vite build) ----
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

# ---- build: bundle the SPA ----
FROM deps AS builder
COPY tsconfig.json vite.config.ts index.html ./
COPY server ./server
COPY src ./src
RUN npm run build

# ---- runtime: server deps only + built SPA ----
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup -g 1001 -S app && adduser -S app -u 1001
# DEM tile volume mounts here; ownership is inherited on first volume creation
RUN mkdir -p /data && chown app:app /data
ENV DEM_PATH=/data/glo30_n20w104.tif
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --omit=dev
COPY server ./server
COPY --from=builder /app/dist ./dist
USER app
EXPOSE 8787
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8787/api/me || exit 1
CMD ["node_modules/.bin/tsx", "server/index.ts"]
