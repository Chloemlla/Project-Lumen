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
    let has_required_tier = state.store.user_has_tier_at_least(&user.id, "PLUS").await?;
    require_commercial_plus_access(has_required_tier)?;
    Ok(user)
}

fn require_commercial_plus_access(has_required_tier: bool) -> Result<(), ApiError> {
    if has_required_tier {
        return Ok(());
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

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{body::to_bytes, response::IntoResponse};
    use serde_json::Value;

    #[test]
    fn commercial_cloud_guard_allows_an_eligible_entitlement() {
        assert!(require_commercial_plus_access(true).is_ok());
    }

    #[test]
    fn protected_sync_and_backup_routes_keep_the_commercial_plus_guard() {
        assert_protected_routes(
            include_str!("routes/sync.rs"),
            &["/sync/changes", "/sync/push"],
        );
        assert_protected_routes(
            include_str!("routes/backups.rs"),
            &["/backups", "/backups/latest"],
        );
    }

    #[tokio::test]
    async fn commercial_cloud_guard_returns_the_stable_forbidden_response() {
        let error = require_commercial_plus_access(false)
            .expect_err("ineligible commercial cloud access should be rejected");
        let response = error.into_response();

        assert_eq!(response.status(), axum::http::StatusCode::FORBIDDEN);
        let body = to_bytes(response.into_body(), usize::MAX)
            .await
            .expect("forbidden response body should be readable");
        let payload: Value =
            serde_json::from_slice(&body).expect("forbidden response should contain valid JSON");

        assert_eq!(
            payload["error"]["reasonCode"],
            Value::String("commercial_plus_required".to_owned())
        );
    }

    fn assert_protected_routes(source: &str, route_paths: &[&str]) {
        for path in route_paths {
            assert!(source.contains(path), "missing protected route {path}");
        }
        assert_eq!(
            source
                .matches("require_plus_entitlement(&headers, &state).await?")
                .count(),
            route_paths.len(),
            "every protected route handler must call the commercial Plus guard"
        );
    }
}
