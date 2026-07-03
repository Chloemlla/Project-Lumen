FROM rust:1-bookworm AS backend-builder

WORKDIR /workspace
COPY backend/Cargo.toml backend/Cargo.lock ./backend/
COPY backend/src ./backend/src
RUN cargo build --manifest-path backend/Cargo.toml --release

FROM node:24-bookworm AS admin-builder

WORKDIR /workspace/backend/admin
COPY backend/admin/package.json ./
COPY backend/admin/tsconfig.json backend/admin/tsconfig.app.json backend/admin/tsconfig.node.json ./
COPY backend/admin/vite.config.ts ./
COPY backend/admin/index.html ./
COPY backend/admin/src ./src
RUN npm install
RUN npm run build
RUN test -f dist/index.html

FROM debian:bookworm-slim

WORKDIR /app
ENV LUMEN_BIND_ADDRESS=0.0.0.0:8080 \
    LUMEN_API_PREFIX=/api \
    LUMEN_ADMIN_STATIC_DIR=/app/backend/admin/dist \
    LUMEN_OUTEMAIL_BASE_URL=https://tts.chloemlla.com \
    LUMEN_OUTEMAIL_FROM=noreply \
    LUMEN_OUTEMAIL_DISPLAY_NAME="Project Lumen" \
    LUMEN_OUTEMAIL_TIMEOUT_SECONDS=10

COPY --from=backend-builder /workspace/backend/target/release/project-lumen-api /usr/local/bin/project-lumen-api
COPY --from=admin-builder /workspace/backend/admin/dist /app/backend/admin/dist
COPY --from=admin-builder /workspace/backend/admin/dist /app/backend/admin

EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/project-lumen-api"]
