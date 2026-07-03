use crate::{
    error::ApiError,
    models::{
        AuthSessionResponse, RefreshSessionRequest, StartEmailRequest, StartEmailResponse,
        VerifyEmailRequest,
    },
    state::AppState,
};
use axum::{extract::State, routing::post, Json, Router};
use uuid::Uuid;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/email/start", post(start_email))
        .route("/email/verify", post(verify_email))
        .route("/session/refresh", post(refresh_session))
}

async fn start_email(
    State(state): State<AppState>,
    Json(payload): Json<StartEmailRequest>,
) -> Result<Json<StartEmailResponse>, ApiError> {
    let outemail = state.outemail.clone();
    let code = if outemail.is_some() {
        generate_login_code()
    } else {
        state.config.login_code.clone()
    };
    let mut response = state
        .store
        .start_email_login(&payload.email, &code, state.config.login_ttl_seconds)
        .await?;

    if let Some(outemail) = outemail {
        outemail
            .send_login_code(payload.email.trim(), &code, state.config.login_ttl_seconds)
            .await
            .map_err(|error| {
                tracing::error!(
                    request_id = %response.request_id,
                    %error,
                    "failed to send login verification email"
                );
                ApiError::Internal
            })?;
        response.delivery = "email".to_owned();
        response.dev_code = None;
    }

    Ok(Json(response))
}

async fn verify_email(
    State(state): State<AppState>,
    Json(payload): Json<VerifyEmailRequest>,
) -> Result<Json<AuthSessionResponse>, ApiError> {
    let response = state
        .store
        .verify_email_login(
            payload,
            state.config.access_token_ttl_seconds,
            state.config.refresh_token_ttl_seconds,
        )
        .await?;
    Ok(Json(response))
}

fn generate_login_code() -> String {
    format!("{:06}", Uuid::new_v4().as_u128() % 1_000_000)
}

async fn refresh_session(
    State(state): State<AppState>,
    Json(payload): Json<RefreshSessionRequest>,
) -> Result<Json<AuthSessionResponse>, ApiError> {
    let response = state
        .store
        .refresh_session(
            payload,
            state.config.access_token_ttl_seconds,
            state.config.refresh_token_ttl_seconds,
        )
        .await?;
    Ok(Json(response))
}
