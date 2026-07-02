use crate::{
    auth_context::require_user,
    error::ApiError,
    models::{TelemetryDebugLatestResponse, TelemetryUploadRequest, TelemetryUploadResponse},
    state::AppState,
};
use axum::{extract::{Query, State}, http::HeaderMap, routing::{get, post}, Json, Router};
use serde::Deserialize;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/telemetry", post(upload))
        .route("/telemetry/debug/latest", get(debug_latest))
}

async fn upload(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(payload): Json<TelemetryUploadRequest>,
) -> Result<Json<TelemetryUploadResponse>, ApiError> {
    let user = require_user(&headers, &state).await?;
    Ok(Json(
        state
            .store
            .record_telemetry_upload(&user.id, payload)
            .await?,
    ))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct TelemetryDebugQuery {
    device_installation_id: Option<String>,
}

async fn debug_latest(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(query): Query<TelemetryDebugQuery>,
) -> Result<Json<TelemetryDebugLatestResponse>, ApiError> {
    let user = require_user(&headers, &state).await?;
    Ok(Json(
        state
            .store
            .latest_telemetry_debug_items(
                &user.id,
                query.device_installation_id.as_deref(),
            )
            .await?,
    ))
}
