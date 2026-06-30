FROM rust:1-bookworm AS backend-builder

WORKDIR /workspace
COPY backend/Cargo.toml backend/Cargo.lock ./backend/
COPY backend/src ./backend/src
RUN cargo build --manifest-path backend/Cargo.toml --release

FROM debian:bookworm-slim

WORKDIR /app
ENV LUMEN_BIND_ADDRESS=0.0.0.0:8080 \
    LUMEN_API_PREFIX=/api \
    LUMEN_ADMIN_STATIC_DIR=/app/backend/admin

COPY --from=backend-builder /workspace/backend/target/release/project-lumen-api /usr/local/bin/project-lumen-api
COPY backend/admin /app/backend/admin

EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/project-lumen-api"]
