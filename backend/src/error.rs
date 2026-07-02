use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde::Serialize;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum ApiError {
    #[error("Bad request: {0}")]
    BadRequest(String),
    #[error("Unauthorized")]
    Unauthorized,
    #[error("Forbidden")]
    Forbidden,
    #[error("Too many requests: {0}")]
    TooManyRequests(String),
    #[error("Not found: {0}")]
    NotFound(String),
    #[error("Conflict: {0}")]
    Conflict(String),
    #[error("Internal server error")]
    Internal,
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let status = match &self {
            ApiError::BadRequest(_) => StatusCode::BAD_REQUEST,
            ApiError::Unauthorized => StatusCode::UNAUTHORIZED,
            ApiError::Forbidden => StatusCode::FORBIDDEN,
            ApiError::TooManyRequests(_) => StatusCode::TOO_MANY_REQUESTS,
            ApiError::NotFound(_) => StatusCode::NOT_FOUND,
            ApiError::Conflict(_) => StatusCode::CONFLICT,
            ApiError::Internal => StatusCode::INTERNAL_SERVER_ERROR,
        };

        let message = self.to_string();
        (status, Json(ErrorResponse::new(status.as_u16(), message))).into_response()
    }
}

#[derive(Serialize)]
struct ErrorResponse {
    error: ErrorPayload,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ErrorPayload {
    code: u16,
    message: String,
}

impl ErrorResponse {
    fn new(code: u16, message: String) -> Self {
        Self {
            error: ErrorPayload { code, message },
        }
    }
}
