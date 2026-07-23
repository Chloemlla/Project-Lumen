use crate::{
    auth_context::require_user,
    error::ApiError,
    models::{
        DeviceControlPolicyResponse, LifecycleEventRequest, LifecycleEventResponse,
        VisionFrameUploadRequest, VisionFrameUploadResponse, VisionHeartbeatRequest,
        VisionHeartbeatResponse, VisionSessionStartRequest, VisionSessionStartResponse,
    },
    state::AppState,
};
use axum::{
    extract::{Query, State},
    http::HeaderMap,
    routing::{get, post},
    Json, Router,
};
use serde::Deserialize;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/device-control/policy", get(get_policy))
        .route(
            "/device-control/vision/sessions",
            post(start_vision_session),
        )
        .route("/device-control/vision/heartbeat", post(vision_heartbeat))
        .route("/device-control/vision/frames", post(upload_vision_frame))
        .route(
            "/device-control/vision/surface-frames",
            post(upload_surface_vision_frame),
        )
        .route(
            "/device-control/lifecycle/events",
            post(report_lifecycle_event),
        )
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct PolicyQuery {
    device_installation_id: Option<String>,
}

async fn get_policy(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(query): Query<PolicyQuery>,
) -> Result<Json<DeviceControlPolicyResponse>, ApiError> {
    let user = require_user(&headers, &state).await?;
    Ok(Json(
        state
            .store
            .get_device_control_policy(&user.id, query.device_installation_id.as_deref())
            .await?,
    ))
}

async fn start_vision_session(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(payload): Json<VisionSessionStartRequest>,
) -> Result<Json<VisionSessionStartResponse>, ApiError> {
    let user = require_user(&headers, &state).await?;
    Ok(Json(
        state.store.start_vision_session(&user.id, payload).await?,
    ))
}

async fn vision_heartbeat(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(payload): Json<VisionHeartbeatRequest>,
) -> Result<Json<VisionHeartbeatResponse>, ApiError> {
    let user = require_user(&headers, &state).await?;
    Ok(Json(
        state
            .store
            .heartbeat_vision_session(&user.id, payload)
            .await?,
    ))
}

async fn upload_vision_frame(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(payload): Json<VisionFrameUploadRequest>,
) -> Result<Json<VisionFrameUploadResponse>, ApiError> {
    let user = require_user(&headers, &state).await?;
    Ok(Json(
        state.store.upload_vision_frame(&user.id, payload).await?,
    ))
}

async fn upload_surface_vision_frame(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(mut payload): Json<VisionFrameUploadRequest>,
) -> Result<Json<VisionFrameUploadResponse>, ApiError> {
    let user = require_user(&headers, &state).await?;
    payload.pipeline = "surface".to_owned();
    payload.surface_attached = true;
    payload.no_surface_preview = false;
    Ok(Json(
        state.store.upload_vision_frame(&user.id, payload).await?,
    ))
}

async fn report_lifecycle_event(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(payload): Json<LifecycleEventRequest>,
) -> Result<Json<LifecycleEventResponse>, ApiError> {
    let user = require_user(&headers, &state).await?;
    Ok(Json(
        state
            .store
            .record_lifecycle_event(&user.id, payload)
            .await?,
    ))
}
