FROM rust:1-bookworm AS backend-builder

WORKDIR /workspace
COPY backend/Cargo.toml backend/Cargo.lock ./backend/
COPY backend/src ./backend/src
RUN cargo build --manifest-path backend/Cargo.toml --release

FROM debian:bookworm-slim

WORKDIR /app
ENV LUMEN_BIND_ADDRESS=0.0.0.0:8080 \
    LUMEN_API_PREFIX=/api \
    LUMEN_ADMIN_STATIC_DIR=/app/backend/admin \
    LUMEN_OUTEMAIL_BASE_URL=https://tts.chloemlla.com \
    LUMEN_OUTEMAIL_FROM=noreply \
    LUMEN_OUTEMAIL_DISPLAY_NAME="Project Lumen" \
    LUMEN_OUTEMAIL_TIMEOUT_SECONDS=10

COPY --from=backend-builder /workspace/backend/target/release/project-lumen-api /usr/local/bin/project-lumen-api
COPY backend/admin /app/backend/admin

EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/project-lumen-api"]
