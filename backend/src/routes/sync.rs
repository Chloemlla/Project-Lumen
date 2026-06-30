use crate::{
    auth_context::require_user,
    error::ApiError,
    models::{SyncChangesQuery, SyncChangesResponse, SyncPushRequest, SyncPushResponse},
    state::AppState,
};
use axum::{
    extract::{Query, State},
    http::HeaderMap,
    routing::{get, post},
    Json, Router,
};

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/sync/changes", get(changes))
        .route("/sync/push", post(push))
}

async fn changes(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(query): Query<SyncChangesQuery>,
) -> Result<Json<SyncChangesResponse>, ApiError> {
    let user = require_user(&headers, &state).await?;
    Ok(Json(
        state
            .store
            .changes_since(&user.id, query.since.unwrap_or_default())
            .await?,
    ))
}

async fn push(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(payload): Json<SyncPushRequest>,
) -> Result<Json<SyncPushResponse>, ApiError> {
    let user = require_user(&headers, &state).await?;
    Ok(Json(state.store.push_changes(&user.id, payload).await?))
}
