use crate::{api, config::Config, state::AppState};
use std::{path::Path, time::Instant};
use tokio::net::TcpListener;
use tower_http::{
    cors::{Any, CorsLayer},
    trace::TraceLayer,
};

type ServerResult<T> = Result<T, Box<dyn std::error::Error + Send + Sync>>;

pub async fn run(config: Config) -> ServerResult<()> {
    let startup_started_at = Instant::now();
    let bind_address = config.bind_address.clone();
    log_startup_config(&config);

    tracing::info!(phase = "state.connect", "connecting application state");
    let state = match AppState::connect(config).await {
        Ok(state) => {
            tracing::info!(
                phase = "state.connect",
                elapsed_ms = elapsed_ms(&startup_started_at),
                "application state connected"
            );
            state
        }
        Err(error) => {
            tracing::error!(
                phase = "state.connect",
                elapsed_ms = elapsed_ms(&startup_started_at),
                %error,
                "failed to connect application state"
            );
            return Err(error.into());
        }
    };

    tracing::info!(
        phase = "router.build",
        api_prefix = %state.config.api_prefix,
        admin_static_dir = %state.config.admin_static_dir,
        "building HTTP router"
    );
    let app = api::router(state)
        .layer(
            CorsLayer::new()
                .allow_origin(Any)
                .allow_methods(Any)
                .allow_headers(Any),
        )
        .layer(TraceLayer::new_for_http());
    tracing::info!(
        phase = "router.build",
        elapsed_ms = elapsed_ms(&startup_started_at),
        "HTTP router built"
    );

    tracing::info!(phase = "listener.bind", %bind_address, "binding HTTP listener");
    let listener = match TcpListener::bind(&bind_address).await {
        Ok(listener) => {
            let local_address = listener
                .local_addr()
                .map(|address| address.to_string())
                .unwrap_or_else(|_| bind_address.clone());
            tracing::info!(
                phase = "listener.bind",
                %bind_address,
                %local_address,
                elapsed_ms = elapsed_ms(&startup_started_at),
                "HTTP listener bound"
            );
            listener
        }
        Err(error) => {
            tracing::error!(
                phase = "listener.bind",
                %bind_address,
                elapsed_ms = elapsed_ms(&startup_started_at),
                %error,
                "failed to bind HTTP listener"
            );
            return Err(error.into());
        }
    };

    tracing::info!(
        phase = "serve.run",
        %bind_address,
        startup_elapsed_ms = elapsed_ms(&startup_started_at),
        "Project Lumen API listening"
    );
    match axum::serve(listener, app).await {
        Ok(()) => {
            tracing::info!(phase = "serve.run", "HTTP server stopped cleanly");
            Ok(())
        }
        Err(error) => {
            tracing::error!(phase = "serve.run", %error, "HTTP server stopped with error");
            Err(error.into())
        }
    }
}

fn log_startup_config(config: &Config) {
    let admin_static_path = Path::new(&config.admin_static_dir);
    let admin_index_path = admin_static_path.join("index.html");
    tracing::info!(
        phase = "config.loaded",
        bind_address = %config.bind_address,
        api_prefix = %config.api_prefix,
        admin_static_dir = %config.admin_static_dir,
        admin_static_exists = admin_static_path.exists(),
        admin_index_exists = admin_index_path.exists(),
        mongodb_uri = %config.redacted_mongodb_uri(),
        mongodb_database = %config.mongodb_database,
        admin_username = %config.admin_username,
        admin_password_configured = !config.admin_password.trim().is_empty(),
        admin_automation_token_configured = !config.admin_automation_token.trim().is_empty(),
        admin_access_token_ttl_seconds = config.admin_access_token_ttl_seconds,
        admin_refresh_token_ttl_seconds = config.admin_refresh_token_ttl_seconds,
        login_code_configured = !config.login_code.trim().is_empty(),
        login_ttl_seconds = config.login_ttl_seconds,
        outemail_configured = config.outemail_configured(),
        outemail_base_url = %config.outemail_base_url,
        outemail_from_configured = !config.outemail_from.trim().is_empty(),
        outemail_display_name = %config.outemail_display_name,
        outemail_domain_configured = !config.outemail_domain.trim().is_empty(),
        outemail_timeout_seconds = config.outemail_timeout_seconds,
        access_token_ttl_seconds = config.access_token_ttl_seconds,
        refresh_token_ttl_seconds = config.refresh_token_ttl_seconds,
        request_signing_secret_configured = !config.request_signing_secret.trim().is_empty(),
        request_timestamp_skew_seconds = config.request_timestamp_skew_seconds,
        require_request_signing = config.require_request_signing,
        accept_unverified_purchases = config.accept_unverified_purchases,
        "startup configuration loaded"
    );

    if config.uses_default_admin_password() {
        tracing::warn!(
            phase = "config.loaded",
            env_var = "LUMEN_ADMIN_PASSWORD",
            "default admin password is configured"
        );
    }
    if config.uses_default_login_code() {
        tracing::warn!(
            phase = "config.loaded",
            env_var = "LUMEN_DEV_LOGIN_CODE",
            "default development login code is configured"
        );
    }
    if config.uses_default_request_signing_secret() {
        tracing::warn!(
            phase = "config.loaded",
            env_var = "LUMEN_REQUEST_SIGNING_SECRET",
            "default request signing secret is configured"
        );
    }
    if config.accept_unverified_purchases {
        tracing::warn!(
            phase = "config.loaded",
            env_var = "LUMEN_ACCEPT_UNVERIFIED_PURCHASES",
            "unverified purchases are accepted"
        );
    }
    if !admin_static_path.exists() {
        tracing::warn!(
            phase = "config.loaded",
            admin_static_dir = %config.admin_static_dir,
            "admin static directory does not exist"
        );
    } else if !admin_index_path.exists() {
        tracing::warn!(
            phase = "config.loaded",
            admin_index_path = %admin_index_path.display(),
            "admin dashboard index file does not exist"
        );
    }
}

fn elapsed_ms(started_at: &Instant) -> u64 {
    started_at.elapsed().as_millis().min(u128::from(u64::MAX)) as u64
}
