use crate::{error::ApiError, models::AdminOperatorDto, state::AppState};
use axum::http::{header::AUTHORIZATION, HeaderMap};
use subtle::ConstantTimeEq;

pub async fn require_admin(
    headers: &HeaderMap,
    state: &AppState,
) -> Result<AdminOperatorDto, ApiError> {
    let token = bearer_token(headers)?;
    state.store.admin_operator_for_token(token).await
}

pub async fn require_admin_action_operator(
    headers: &HeaderMap,
    state: &AppState,
) -> Result<AdminOperatorDto, ApiError> {
    let token = bearer_token(headers)?;
    if automation_token_matches(token, &state.config.admin_automation_token) {
        return Ok(AdminOperatorDto {
            id: "release-workflow".to_owned(),
            username: "release-workflow".to_owned(),
            role: "automation".to_owned(),
        });
    }
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

fn automation_token_matches(token: &str, configured_token: &str) -> bool {
    let configured_token = configured_token.trim();
    configured_token.len() >= 32
        && token
            .as_bytes()
            .ct_eq(configured_token.as_bytes())
            .unwrap_u8()
            == 1
}
