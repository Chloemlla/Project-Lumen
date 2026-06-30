use crate::state::AppState;
use axum::{middleware, Router};
use tower_http::services::{ServeDir, ServeFile};

#[path = "routes/backups.rs"]
pub mod backups;
#[path = "routes/admin.rs"]
pub mod admin;
#[path = "routes/audit.rs"]
pub mod audit;
#[path = "routes/entitlements.rs"]
pub mod entitlements;
#[path = "routes/health.rs"]
pub mod health;
#[path = "routes/me.rs"]
pub mod me;
#[path = "routes/purchases.rs"]
pub mod purchases;
#[path = "routes/session.rs"]
pub mod session;
#[path = "routes/sync.rs"]
pub mod sync;

pub fn router(state: AppState) -> Router {
    let prefix = state.config.api_prefix.clone();
    let admin_static_dir = state.config.admin_static_dir.clone();
    let api = Router::new()
        .route("/health", axum::routing::get(health::health))
        .nest("/admin", admin::router())
        .nest("/v1/auth", session::router())
        .nest("/v1", me::router())
        .nest("/v1", entitlements::router())
        .nest("/v1", purchases::router())
        .nest("/v1", sync::router())
        .nest("/v1", backups::router())
        .layer(middleware::from_fn_with_state(state.clone(), audit::audit_request));

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
