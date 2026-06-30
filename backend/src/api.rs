use crate::state::AppState;
use axum::Router;

#[path = "routes/backups.rs"]
pub mod backups;
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
    let api = Router::new()
        .route("/health", axum::routing::get(health::health))
        .nest("/v1/auth", session::router())
        .nest("/v1", me::router())
        .nest("/v1", entitlements::router())
        .nest("/v1", purchases::router())
        .nest("/v1", sync::router())
        .nest("/v1", backups::router());

    Router::new().nest(&prefix, api).with_state(state)
}
