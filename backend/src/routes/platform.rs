use crate::{
    error::ApiError,
    models::{AdminReleaseAssetItem, AdminReleaseItem, AdminReleasePatchItem},
    state::AppState,
};
use axum::{
    extract::{Query, State},
    routing::get,
    Json, Router,
};
use serde::Deserialize;
use serde_json::{json, Value};
use std::{
    collections::hash_map::DefaultHasher,
    hash::{Hash, Hasher},
};

pub fn public_router() -> Router<AppState> {
    Router::new().route("/openapi.json", get(openapi))
}

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/config/feature-flags", get(feature_flags))
        .route("/config/sync", get(config_sync))
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
            "/v1/config/sync": { "get": { "summary": "Fetch cursor-based templates, feature flags, and remote policies" } },
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
    let fetched_at = now_millis();
    Json(json!({
        "fetchedAt": fetched_at,
        "flags": feature_flag_payload(CONFIG_STATIC_CURSOR)
    }))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ConfigSyncQuery {
    cursor: Option<i64>,
    version: Option<i64>,
    channel: Option<String>,
}

async fn config_sync(
    State(state): State<AppState>,
    Query(query): Query<ConfigSyncQuery>,
) -> Result<Json<Value>, ApiError> {
    let cursor = query.cursor.unwrap_or_default().max(0);
    let mut next_cursor = cursor;
    let flags = if cursor < CONFIG_STATIC_CURSOR {
        next_cursor = next_cursor.max(CONFIG_STATIC_CURSOR);
        feature_flag_payload(CONFIG_STATIC_CURSOR)
    } else {
        Vec::new()
    };
    let policies = if cursor < CONFIG_STATIC_CURSOR {
        next_cursor = next_cursor.max(CONFIG_STATIC_CURSOR);
        remote_policy_payload(CONFIG_STATIC_CURSOR)
    } else {
        Vec::new()
    };
    let templates = state
        .store
        .template_catalog()
        .await?
        .into_iter()
        .filter(|template| template.updated_at > cursor)
        .map(|template| {
            next_cursor = next_cursor.max(template.updated_at);
            json!({
                "id": template.id,
                "name": template.name,
                "tier": template.tier,
                "countdownStyle": template.countdown_style,
                "color": template.color,
                "locales": template.locales,
                "layoutJson": template.layout_json,
                "updatedAt": template.updated_at
            })
        })
        .collect::<Vec<_>>();

    Ok(Json(json!({
        "schemaVersion": query.version.unwrap_or(1).max(1),
        "cursor": next_cursor,
        "serverTime": now_millis(),
        "channel": query.channel.unwrap_or_else(|| DEFAULT_CHANNEL.to_owned()),
        "featureFlags": flags,
        "templates": templates,
        "policies": policies
    })))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ReleaseCheckQuery {
    current_version_code: Option<i64>,
    abi: Option<String>,
    channel: Option<String>,
    rollout_key: Option<String>,
}

async fn check_release(
    State(state): State<AppState>,
    Query(query): Query<ReleaseCheckQuery>,
) -> Result<Json<Value>, ApiError> {
    let current_version_code = query.current_version_code.unwrap_or_default();
    let requested_abi = query.abi.unwrap_or_else(|| "universal".to_owned());
    let requested_channel = query.channel.unwrap_or_else(|| DEFAULT_CHANNEL.to_owned());
    let normalized_channel = normalize_channel(&requested_channel);
    let releases = state.store.releases().await?;
    let candidate = releases
        .into_iter()
        .filter(|release| release.version_code > current_version_code)
        .filter(|release| channel_matches(&release.channel, &normalized_channel))
        .filter(|release| rollout_allows(release, query.rollout_key.as_deref()))
        .max_by_key(|release| release.version_code);
    let Some(release) = candidate else {
        return Ok(Json(json!({
            "updateAvailable": false,
            "currentVersionCode": current_version_code,
            "checkedAt": now_millis(),
            "channel": requested_channel,
            "abi": requested_abi
        })));
    };
    let selected_asset = select_release_asset(&release.assets, &requested_abi);
    let selected_abi = selected_asset
        .map(|asset| asset.abi.as_str())
        .unwrap_or(requested_abi.as_str());
    let full_apk_url = selected_asset
        .map(|asset| asset.url.as_str())
        .unwrap_or_default();
    let full_apk_sha256 = selected_asset
        .map(|asset| asset.sha256.as_str())
        .unwrap_or(release.sha256.as_str());
    let full_apk_size_bytes = selected_asset
        .map(|asset| asset.size_bytes)
        .unwrap_or_default();
    Ok(Json(json!({
        "updateAvailable": true,
        "currentVersionCode": current_version_code,
        "versionCode": release.version_code,
        "versionName": release.version_name,
        "tagName": format!("v{}", release.version_name),
        "releaseUrl": release.release_url,
        "sha256": release.sha256,
        "fullApkUrl": full_apk_url,
        "fullApkSha256": full_apk_sha256,
        "fullApkSizeBytes": full_apk_size_bytes,
        "rollout": release.rollout,
        "forceUpdate": release.force_update,
        "createdAt": release.created_at,
        "checkedAt": now_millis(),
        "channel": release.channel,
        "abi": selected_abi,
        "assets": release.assets.iter().map(release_asset_json).collect::<Vec<_>>(),
        "patches": release.patches.iter().map(release_patch_json).collect::<Vec<_>>()
    })))
}

fn feature_flag_payload(updated_at: i64) -> Vec<Value> {
    vec![
        json!({
            "key": "cloud_sync",
            "enabled": true,
            "payload": { "scope": ["settings", "stats", "templates", "goals", "plans"] },
            "updatedAt": updated_at,
            "version": CONFIG_STATIC_CURSOR
        }),
        json!({
            "key": "remote_entitlements",
            "enabled": true,
            "payload": { "source": "server" },
            "updatedAt": updated_at,
            "version": CONFIG_STATIC_CURSOR
        }),
        json!({
            "key": "telemetry_upload",
            "enabled": true,
            "payload": { "requiresConsent": true, "rateLimitPerMinute": 12 },
            "updatedAt": updated_at,
            "version": CONFIG_STATIC_CURSOR
        }),
        json!({
            "key": "face_analysis_upload",
            "enabled": false,
            "payload": { "status": "planned", "requiresExplicitConsent": true },
            "updatedAt": updated_at,
            "version": CONFIG_STATIC_CURSOR
        }),
    ]
}

fn remote_policy_payload(updated_at: i64) -> Vec<Value> {
    vec![
        json!({
            "key": "release_manifest",
            "enabled": true,
            "payload": {
                "endpoint": "/v1/releases/check",
                "fullApkFallbackRequired": true,
                "patchesOptional": true
            },
            "updatedAt": updated_at,
            "version": CONFIG_STATIC_CURSOR
        }),
        json!({
            "key": "config_sync",
            "enabled": true,
            "payload": {
                "endpoint": "/v1/config/sync",
                "collections": ["templates", "featureFlags", "policies"]
            },
            "updatedAt": updated_at,
            "version": CONFIG_STATIC_CURSOR
        }),
    ]
}

fn select_release_asset<'a>(
    assets: &'a [AdminReleaseAssetItem],
    requested_abi: &str,
) -> Option<&'a AdminReleaseAssetItem> {
    let normalized_abi = normalize_abi(requested_abi);
    assets
        .iter()
        .find(|asset| normalize_abi(&asset.abi) == normalized_abi)
        .or_else(|| {
            assets
                .iter()
                .find(|asset| matches!(normalize_abi(&asset.abi).as_str(), "universal" | "all"))
        })
        .or_else(|| assets.first())
}

fn release_asset_json(asset: &AdminReleaseAssetItem) -> Value {
    json!({
        "abi": asset.abi,
        "name": asset.name,
        "url": asset.url,
        "sha256": asset.sha256,
        "sizeBytes": asset.size_bytes,
        "contentType": asset.content_type
    })
}

fn release_patch_json(patch: &AdminReleasePatchItem) -> Value {
    json!({
        "fromVersionCode": patch.from_version_code,
        "fromSha256": patch.from_sha256,
        "toSha256": patch.to_sha256,
        "patchUrl": patch.patch_url,
        "patchSha256": patch.patch_sha256,
        "algorithm": patch.algorithm,
        "sizeBytes": patch.size_bytes
    })
}

fn channel_matches(release_channel: &str, requested_channel: &str) -> bool {
    let release_channel = normalize_channel(release_channel);
    release_channel == requested_channel
        || (requested_channel != DEFAULT_CHANNEL && release_channel == DEFAULT_CHANNEL)
}

fn rollout_allows(release: &AdminReleaseItem, rollout_key: Option<&str>) -> bool {
    let rollout = release.rollout.trim().to_ascii_lowercase();
    if rollout.is_empty() || matches!(rollout.as_str(), "all" | "stable" | "100" | "100%") {
        return true;
    }
    if matches!(rollout.as_str(), "blocked" | "paused" | "0" | "0%") {
        return false;
    }
    let Some(percent) = rollout_percent(&rollout) else {
        return true;
    };
    if percent <= 0.0 {
        return false;
    }
    if percent >= 100.0 {
        return true;
    }
    let Some(key) = rollout_key.map(str::trim).filter(|value| !value.is_empty()) else {
        return false;
    };
    rollout_bucket(key, release.version_code) < percent
}

fn rollout_percent(value: &str) -> Option<f64> {
    value
        .trim_end_matches('%')
        .parse::<f64>()
        .ok()
        .map(|percent| percent.clamp(0.0, 100.0))
}

fn rollout_bucket(key: &str, version_code: i64) -> f64 {
    let mut hasher = DefaultHasher::new();
    key.hash(&mut hasher);
    version_code.hash(&mut hasher);
    (hasher.finish() % 10_000) as f64 / 100.0
}

fn normalize_channel(value: &str) -> String {
    value
        .trim()
        .to_ascii_lowercase()
        .replace('_', "-")
        .if_empty(DEFAULT_CHANNEL)
}

fn normalize_abi(value: &str) -> String {
    value.trim().to_ascii_lowercase().replace('-', "_")
}

fn now_millis() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|duration| duration.as_millis() as i64)
        .unwrap_or_default()
}

trait EmptyFallback {
    fn if_empty(self, fallback: &str) -> String;
}

impl EmptyFallback for String {
    fn if_empty(self, fallback: &str) -> String {
        if self.trim().is_empty() {
            fallback.to_owned()
        } else {
            self
        }
    }
}

const DEFAULT_CHANNEL: &str = "stable";
const CONFIG_STATIC_CURSOR: i64 = 1;
