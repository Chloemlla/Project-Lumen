use crate::{error::ApiError, models::AdminOperatorDto, state::AppState};
use axum::http::{header::AUTHORIZATION, HeaderMap};

pub async fn require_admin(
    headers: &HeaderMap,
    state: &AppState,
) -> Result<AdminOperatorDto, ApiError> {
    let token = bearer_token(headers)?;
    state.store.admin_operator_for_token(token).await
}

fn bearer_token(headers: &HeaderMap) -> Result<&str, ApiError> {
    let value = headers
        .get(AUTHORIZATION)
        .and_then(|header| header.to_str().ok())
        .ok_or(ApiError::Unauthorized)?;

    value
        .strip_prefix("Bearer ")
        .filter(|token| !token.trim().is_empty())
        .map(str::trim)
        .ok_or(ApiError::Unauthorized)
}
