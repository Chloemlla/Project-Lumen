use crate::{error::ApiError, state::AppState};
use axum::{
    extract::{Query, State},
    routing::get,
    Json, Router,
};
use serde::Deserialize;
use serde_json::{json, Value};

pub fn public_router() -> Router<AppState> {
    Router::new().route("/openapi.json", get(openapi))
}

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/config/feature-flags", get(feature_flags))
        .route("/releases/check", get(check_release))
}

async fn openapi(State(state): State<AppState>) -> Json<Value> {
    Json(json!({
        "openapi": "3.0.3",
        "info": {
            "title": "Project Lumen API",
            "version": env!("CARGO_PKG_VERSION"),
            "description": "Account, sync, entitlements, telemetry, backup, feature flag, and release APIs for Project Lumen Android."
        },
        "servers": [{ "url": state.config.api_prefix.as_str() }],
        "security": [{ "bearerAuth": [] }],
        "paths": {
            "/health": { "get": { "summary": "Health check", "security": [] } },
            "/v1/auth/email/start": { "post": { "summary": "Start email verification login", "security": [] } },
            "/v1/auth/email/verify": { "post": { "summary": "Verify code and issue access/refresh tokens", "security": [] } },
            "/v1/auth/session/refresh": { "post": { "summary": "Refresh an access token" } },
            "/v1/me": { "get": { "summary": "Current authenticated user" } },
            "/v1/devices/register": { "post": { "summary": "Register or refresh a device installation record" } },
            "/v1/entitlements": { "get": { "summary": "Current user entitlement snapshot" } },
            "/v1/purchases/google/verify": { "post": { "summary": "Verify Google Play purchase token" } },
            "/v1/sync/changes": { "get": { "summary": "Fetch incremental sync changes" } },
            "/v1/sync/push": { "post": { "summary": "Push incremental sync changes" } },
            "/v1/backups": { "post": { "summary": "Upload a full JSON cloud backup" } },
            "/v1/backups/latest": { "get": { "summary": "Fetch latest JSON cloud backup" } },
            "/v1/telemetry": { "post": { "summary": "Upload eye-care telemetry" } },
            "/v1/telemetry/debug/latest": { "get": { "summary": "Fetch latest telemetry debug rows" } },
            "/v1/face-analysis/frames": { "post": { "summary": "Upload face-analysis metadata frame" } },
            "/v1/config/feature-flags": { "get": { "summary": "Fetch remote feature flags" } },
            "/v1/releases/check": { "get": { "summary": "Check channel-aware Android release availability" } }
        },
        "components": {
            "securitySchemes": {
                "bearerAuth": { "type": "http", "scheme": "bearer", "bearerFormat": "JWT" },
                "lumenRequestSignature": {
                    "type": "apiKey",
                    "in": "header",
                    "name": "x-lumen-signature",
                    "description": "HMAC signature generated from method, path, query, body hash, timestamp, and nonce."
                },
                "playIntegrity": {
                    "type": "apiKey",
                    "in": "header",
                    "name": "x-lumen-integrity"
                }
            }
        }
    }))
}

async fn feature_flags() -> Json<Value> {
    Json(json!({
        "fetchedAt": now_millis(),
        "flags": [
            {
                "key": "cloud_sync",
                "enabled": true,
                "payload": { "scope": ["settings", "stats", "templates", "goals", "plans"] }
            },
            {
                "key": "remote_entitlements",
                "enabled": true,
                "payload": { "source": "server" }
            },
            {
                "key": "telemetry_upload",
                "enabled": true,
                "payload": { "requiresConsent": true, "rateLimitPerMinute": 12 }
            },
            {
                "key": "face_analysis_upload",
                "enabled": false,
                "payload": { "status": "planned", "requiresExplicitConsent": true }
            }
        ]
    }))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ReleaseCheckQuery {
    current_version_code: Option<i64>,
    abi: Option<String>,
    channel: Option<String>,
}

async fn check_release(
    State(state): State<AppState>,
    Query(query): Query<ReleaseCheckQuery>,
) -> Result<Json<Value>, ApiError> {
    let current_version_code = query.current_version_code.unwrap_or_default();
    let releases = state.store.releases().await?;
    let candidate = releases
        .into_iter()
        .filter(|release| release.version_code > current_version_code)
        .max_by_key(|release| release.version_code);
    let Some(release) = candidate else {
        return Ok(Json(json!({
            "updateAvailable": false,
            "currentVersionCode": current_version_code,
            "checkedAt": now_millis(),
            "channel": query.channel.unwrap_or_else(|| "stable".to_owned()),
            "abi": query.abi.unwrap_or_else(|| "universal".to_owned())
        })));
    };
    Ok(Json(json!({
        "updateAvailable": true,
        "currentVersionCode": current_version_code,
        "versionCode": release.version_code,
        "versionName": release.version_name,
        "sha256": release.sha256,
        "rollout": release.rollout,
        "forceUpdate": release.force_update,
        "createdAt": release.created_at,
        "checkedAt": now_millis(),
        "channel": query.channel.unwrap_or_else(|| "stable".to_owned()),
        "abi": query.abi.unwrap_or_else(|| "universal".to_owned())
    })))
}

fn now_millis() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|duration| duration.as_millis() as i64)
        .unwrap_or_default()
}
