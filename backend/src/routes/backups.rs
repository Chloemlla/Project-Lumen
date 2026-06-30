use crate::{
    auth_context::require_user,
    error::ApiError,
    models::{BackupMetadata, BackupUploadRequest, LatestBackupResponse},
    state::AppState,
};
use axum::{extract::State, http::HeaderMap, routing::{get, post}, Json, Router};

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/backups", post(upload_backup))
        .route("/backups/latest", get(latest_backup))
}

async fn upload_backup(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(payload): Json<BackupUploadRequest>,
) -> Result<Json<BackupMetadata>, ApiError> {
    let user = require_user(&headers, &state).await?;
    Ok(Json(state.store.save_backup(&user.id, payload).await?))
}

async fn latest_backup(
    State(state): State<AppState>,
    headers: HeaderMap,
) -> Result<Json<LatestBackupResponse>, ApiError> {
    let user = require_user(&headers, &state).await?;
    Ok(Json(LatestBackupResponse {
        backup: state.store.latest_backup(&user.id).await?,
    }))
}
