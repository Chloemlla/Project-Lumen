use crate::{error::ApiError, store::UserRecord};
use axum::http::{header::AUTHORIZATION, HeaderMap};

use crate::state::AppState;

pub async fn require_user(headers: &HeaderMap, state: &AppState) -> Result<UserRecord, ApiError> {
    let token = bearer_token(headers)?;
    state.store.user_for_token(token).await
}

pub async fn require_plus_entitlement(
    headers: &HeaderMap,
    state: &AppState,
) -> Result<UserRecord, ApiError> {
    let user = require_user(headers, state).await?;
    if state.store.user_has_tier_at_least(&user.id, "PLUS").await? {
        return Ok(user);
    }
    Err(ApiError::forbidden_reason(
        "commercial_plus_required",
        "Project Lumen Commercial Edition Plus entitlement is required for cloud sync and backup.",
    ))
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
