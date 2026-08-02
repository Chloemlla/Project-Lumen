use crate::{error::ApiError, store::UserRecord};
use axum::http::{header::AUTHORIZATION, HeaderMap};

use crate::state::AppState;

pub async fn require_user(headers: &HeaderMap, state: &AppState) -> Result<UserRecord, ApiError> {
    let token = bearer_token(headers)?;
    state.store.user_for_token(token).await
}

pub async fn require_device_security(
    user: &UserRecord,
    requested_device_id: &str,
) -> Result<(), ApiError> {
    if requested_device_id.trim().is_empty() || requested_device_id.trim() != user.device_installation_id {
        return Err(ApiError::forbidden_reason(
            "device_security_required",
            "A recent verified device security status is required for this operation.",
        ));
    }
    let Some(serde_json::Value::Object(evidence)) = user.device_security_evidence.as_ref() else {
        return Err(ApiError::forbidden_reason(
            "device_security_required",
            "A recent verified device security status is required for this operation.",
        ));
    };
    let observed_at = evidence.get("observedAt").and_then(serde_json::Value::as_i64).unwrap_or_default();
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|duration| duration.as_millis() as i64)
        .unwrap_or_default();
    let clean = evidence.get("status").and_then(serde_json::Value::as_str) == Some("clean")
        && evidence.get("completed").and_then(serde_json::Value::as_bool) == Some(true)
        && evidence.get("rooted").and_then(serde_json::Value::as_bool) == Some(false)
        && evidence.get("suspicious").and_then(serde_json::Value::as_bool) == Some(false)
        && evidence.get("hardwareIntegrityOk").and_then(serde_json::Value::as_bool) != Some(false)
        && evidence.get("selinuxEnforcing").and_then(serde_json::Value::as_bool) != Some(false)
        && evidence.get("teeAttestationOk").and_then(serde_json::Value::as_bool) != Some(false)
        && observed_at > 0
        && now.saturating_sub(observed_at) <= DEVICE_SECURITY_MAX_AGE_MILLIS;
    if !clean {
        return Err(ApiError::forbidden_reason(
            "device_security_required",
            "A recent verified device security status is required for this operation.",
        ));
    }
    Ok(())
}

const DEVICE_SECURITY_MAX_AGE_MILLIS: i64 = 15 * 60 * 1_000;

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
