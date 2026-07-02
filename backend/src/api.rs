use crate::state::AppState;
use axum::{middleware, Router};
use std::path::PathBuf;
use tower_http::services::{ServeDir, ServeFile};

#[path = "routes/admin.rs"]
pub mod admin;
#[path = "routes/audit.rs"]
pub mod audit;
#[path = "routes/backups.rs"]
pub mod backups;
#[path = "routes/devices.rs"]
pub mod devices;
#[path = "routes/entitlements.rs"]
pub mod entitlements;
#[path = "routes/face_analysis.rs"]
pub mod face_analysis;
#[path = "routes/health.rs"]
pub mod health;
#[path = "routes/me.rs"]
pub mod me;
#[path = "routes/platform.rs"]
pub mod platform;
#[path = "routes/purchases.rs"]
pub mod purchases;
#[path = "routes/security.rs"]
pub mod security;
#[path = "routes/session.rs"]
pub mod session;
#[path = "routes/sync.rs"]
pub mod sync;
#[path = "routes/telemetry.rs"]
pub mod telemetry;

pub fn router(state: AppState) -> Router {
    let prefix = state.config.api_prefix.clone();
    let admin_static_dir = state.config.admin_static_dir.clone();
    let admin_static_path = PathBuf::from(&admin_static_dir);
    let admin_assets_dir = admin_static_path.join("assets");
    let admin_index_file = admin_static_path.join("index.html");
    let v1 = Router::new()
        .nest("/auth", session::router())
        .merge(me::router())
        .merge(devices::router())
        .merge(entitlements::router())
        .merge(face_analysis::router())
        .merge(platform::router())
        .merge(purchases::router())
        .merge(sync::router())
        .merge(telemetry::router())
        .merge(backups::router())
        .layer(middleware::from_fn_with_state(
            state.clone(),
            security::enforce_api_security,
        ));
    let api = Router::new()
        .route("/health", axum::routing::get(health::health))
        .merge(platform::public_router())
        .nest("/admin", admin::router())
        .nest("/v1", v1)
        .layer(middleware::from_fn_with_state(
            state.clone(),
            audit::audit_request,
        ));

    Router::new()
        .nest(&prefix, api)
        .nest_service("/assets", ServeDir::new(admin_assets_dir))
        .nest_service(
            "/admin",
            ServeDir::new(&admin_static_dir)
                .append_index_html_on_directories(true)
                .fallback(ServeFile::new(admin_index_file)),
        )
        .with_state(state)
}
