use crate::{
    auth_context::require_user,
    error::ApiError,
    models::{DeviceRegistrationRequest, DeviceRegistrationResponse},
    state::AppState,
};
use axum::{extract::State, http::HeaderMap, routing::post, Json, Router};

pub fn router() -> Router<AppState> {
    Router::new().route("/devices/register", post(register_device))
}

async fn register_device(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(payload): Json<DeviceRegistrationRequest>,
) -> Result<Json<DeviceRegistrationResponse>, ApiError> {
    let user = require_user(&headers, &state).await?;
    Ok(Json(
        state.store.register_device_asset(&user.id, payload).await?,
    ))
}
