use crate::{auth_context::require_user, error::ApiError, models::MeResponse, state::AppState};
use axum::{extract::State, http::HeaderMap, routing::get, Json, Router};

pub fn router() -> Router<AppState> {
    Router::new().route("/me", get(me))
}

async fn me(State(state): State<AppState>, headers: HeaderMap) -> Result<Json<MeResponse>, ApiError> {
    let user = require_user(&headers, &state).await?;
    Ok(Json(MeResponse { user: user.to_dto() }))
}
