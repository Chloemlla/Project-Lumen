use crate::{
    auth_context::require_user,
    error::ApiError,
    models::{FaceAnalysisFrameUploadRequest, FaceAnalysisFrameUploadResponse},
    state::AppState,
};
use axum::{
    extract::State,
    http::HeaderMap,
    routing::post,
    Json, Router,
};

pub fn router() -> Router<AppState> {
    Router::new().route("/face-analysis/frames", post(upload_frame))
}

async fn upload_frame(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(payload): Json<FaceAnalysisFrameUploadRequest>,
) -> Result<Json<FaceAnalysisFrameUploadResponse>, ApiError> {
    validate_frame_upload(&payload)?;
    let user = require_user(&headers, &state).await?;
    Ok(Json(
        state
            .store
            .record_face_analysis_frame(&user.id, payload)
            .await?,
    ))
}

fn validate_frame_upload(payload: &FaceAnalysisFrameUploadRequest) -> Result<(), ApiError> {
    if payload.device_installation_id.trim().is_empty() {
        return Err(ApiError::BadRequest(
            "deviceInstallationId is required for face analysis frame upload".to_owned(),
        ));
    }
    if payload.frame.width <= 0 || payload.frame.height <= 0 {
        return Err(ApiError::BadRequest(
            "frame width and height must be positive".to_owned(),
        ));
    }
    if payload.frame.byte_size <= 0 || payload.frame.data_base64.trim().is_empty() {
        return Err(ApiError::BadRequest(
            "frame dataBase64 and byteSize are required".to_owned(),
        ));
    }
    if payload.frame.data_base64.len() > MAX_FRAME_BASE64_LENGTH {
        return Err(ApiError::BadRequest(
            "frame dataBase64 exceeds the accepted realtime upload size".to_owned(),
        ));
    }
    if !matches!(payload.frame.encoding.as_str(), "base64") {
        return Err(ApiError::BadRequest(
            "frame encoding must be base64".to_owned(),
        ));
    }
    Ok(())
}

const MAX_FRAME_BASE64_LENGTH: usize = 2_800_000;
