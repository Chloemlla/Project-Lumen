use crate::models::HealthResponse;
use axum::Json;

pub async fn health() -> Json<HealthResponse> {
    Json(HealthResponse {
        status: "ok".to_owned(),
        service: "project-lumen-api".to_owned(),
        version: env!("CARGO_PKG_VERSION").to_owned(),
    })
}
