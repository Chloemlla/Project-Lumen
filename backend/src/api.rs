use crate::state::AppState;
use axum::{middleware, Router};
use tower_http::services::{ServeDir, ServeFile};

#[path = "routes/admin.rs"]
pub mod admin;
#[path = "routes/audit.rs"]
pub mod audit;
#[path = "routes/backups.rs"]
pub mod backups;
#[path = "routes/entitlements.rs"]
pub mod entitlements;
#[path = "routes/face_analysis.rs"]
pub mod face_analysis;
#[path = "routes/health.rs"]
pub mod health;
#[path = "routes/me.rs"]
pub mod me;
#[path = "routes/purchases.rs"]
pub mod purchases;
#[path = "routes/session.rs"]
pub mod session;
#[path = "routes/security.rs"]
pub mod security;
#[path = "routes/sync.rs"]
pub mod sync;
#[path = "routes/telemetry.rs"]
pub mod telemetry;

pub fn router(state: AppState) -> Router {
    let prefix = state.config.api_prefix.clone();
    let admin_static_dir = state.config.admin_static_dir.clone();
    let v1 = Router::new()
        .nest("/auth", session::router())
        .merge(me::router())
        .merge(entitlements::router())
        .merge(face_analysis::router())
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
        .nest("/admin", admin::router())
        .nest("/v1", v1)
        .layer(middleware::from_fn_with_state(
            state.clone(),
            audit::audit_request,
        ));

    Router::new()
        .nest(&prefix, api)
        .nest_service(
            "/admin",
            ServeDir::new(&admin_static_dir)
                .append_index_html_on_directories(true)
                .fallback(ServeFile::new(format!("{admin_static_dir}/index.html"))),
        )
        .with_state(state)
}
