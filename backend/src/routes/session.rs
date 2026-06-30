use crate::{
    error::ApiError,
    models::{AuthSessionResponse, StartEmailRequest, StartEmailResponse, VerifyEmailRequest},
    state::AppState,
};
use axum::{extract::State, routing::post, Json, Router};

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/email/start", post(start_email))
        .route("/email/verify", post(verify_email))
}

async fn start_email(
    State(state): State<AppState>,
    Json(payload): Json<StartEmailRequest>,
) -> Result<Json<StartEmailResponse>, ApiError> {
    let response = state
        .store
        .start_email_login(
            &payload.email,
            &state.config.login_code,
            state.config.login_ttl_seconds,
        )
        .await?;
    Ok(Json(response))
}

async fn verify_email(
    State(state): State<AppState>,
    Json(payload): Json<VerifyEmailRequest>,
) -> Result<Json<AuthSessionResponse>, ApiError> {
    let response = state
        .store
        .verify_email_login(payload, state.config.access_token_ttl_seconds)
        .await?;
    Ok(Json(response))
}
