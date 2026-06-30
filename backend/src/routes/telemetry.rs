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
use serde_json::Value;

pub fn router() -> Router<AppState> {
    Router::new().route("/telemetry", post(upload))
}

async fn upload(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(raw_payload): Json<Value>,
) -> Result<Json<TelemetryUploadResponse>, ApiError> {
    reject_sensitive_payload(&raw_payload)?;
    let payload = serde_json::from_value::<TelemetryUploadRequest>(raw_payload)
        .map_err(|error| ApiError::BadRequest(format!("Invalid telemetry payload: {error}")))?;
    let user = require_user(&headers, &state).await?;
    Ok(Json(
        state
            .store
            .record_telemetry_upload(&user.id, payload)
            .await?,
    ))
}

fn reject_sensitive_payload(value: &Value) -> Result<(), ApiError> {
    match value {
        Value::Object(map) => {
            for (key, child) in map {
                if is_sensitive_key(key) {
                    return Err(ApiError::BadRequest(
                        "Telemetry payload must not include raw camera media or absolute face landmark coordinates."
                            .to_owned(),
                    ));
                }
                reject_sensitive_payload(child)?;
            }
        }
        Value::Array(items) => {
            for item in items {
                reject_sensitive_payload(item)?;
            }
        }
        _ => {}
    }
    Ok(())
}

fn is_sensitive_key(key: &str) -> bool {
    let normalized: String = key
        .chars()
        .filter(|character| character.is_ascii_alphanumeric())
        .map(|character| character.to_ascii_lowercase())
        .collect();
    matches!(
        normalized.as_str(),
        "rawframe"
            | "rawframebytes"
            | "rawframebase64"
            | "cameraframe"
            | "jpegbytes"
            | "framebase64"
            | "image"
            | "imagebytes"
            | "imagebase64"
            | "photo"
            | "photobase64"
            | "video"
            | "videobytes"
            | "videobase64"
            | "mediapayload"
            | "facelandmarks"
            | "facecontourpoints"
            | "landmarkcoordinates"
            | "absolutefacepoints"
            | "facepointcoordinates"
            | "mlkitfacecontours"
    )
}
