use crate::{
    auth_context::require_user, error::ApiError, models::EntitlementsResponse, state::AppState,
};
use axum::{extract::State, http::HeaderMap, routing::get, Json, Router};

pub fn router() -> Router<AppState> {
    Router::new().route("/entitlements", get(entitlements))
}

async fn entitlements(
    State(state): State<AppState>,
    headers: HeaderMap,
) -> Result<Json<EntitlementsResponse>, ApiError> {
    let user = require_user(&headers, &state).await?;
    Ok(Json(state.store.list_entitlements(&user.id).await?))
}
