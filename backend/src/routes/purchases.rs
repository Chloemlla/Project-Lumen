use crate::{
    auth_context::require_user,
    error::ApiError,
    models::{GooglePurchaseVerifyRequest, PurchaseVerifyResponse},
    state::AppState,
};
use axum::{extract::State, http::HeaderMap, routing::post, Json, Router};

pub fn router() -> Router<AppState> {
    Router::new().route("/purchases/google/verify", post(verify_google_purchase))
}

async fn verify_google_purchase(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(payload): Json<GooglePurchaseVerifyRequest>,
) -> Result<Json<PurchaseVerifyResponse>, ApiError> {
    let user = require_user(&headers, &state).await?;
    let response = state
        .store
        .verify_google_purchase(&user.id, payload, state.config.accept_unverified_purchases)
        .await?;
    Ok(Json(response))
}
