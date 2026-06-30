use crate::{
    auth_context::require_user,
    error::ApiError,
    models::{TelemetryUploadRequest, TelemetryUploadResponse},
    state::AppState,
};
use axum::{
    extract::State,
    http::HeaderMap,
    routing::post,
    Json, Router,
};

pub fn router() -> Router<AppState> {
    Router::new().route("/telemetry", post(upload))
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
