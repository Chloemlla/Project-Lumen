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
    #[error("{message}")]
    ForbiddenReason {
        reason_code: &'static str,
        message: &'static str,
    },
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
            ApiError::Forbidden | ApiError::ForbiddenReason { .. } => StatusCode::FORBIDDEN,
            ApiError::TooManyRequests(_) => StatusCode::TOO_MANY_REQUESTS,
            ApiError::NotFound(_) => StatusCode::NOT_FOUND,
            ApiError::Conflict(_) => StatusCode::CONFLICT,
            ApiError::Internal => StatusCode::INTERNAL_SERVER_ERROR,
        };

        let reason_code = self.reason_code();
        let message = self.to_string();
        (status, Json(ErrorResponse::new(status.as_u16(), reason_code, message))).into_response()
    }
}

impl ApiError {
    pub fn forbidden_reason(reason_code: &'static str, message: &'static str) -> Self {
        Self::ForbiddenReason {
            reason_code,
            message,
        }
    }

    fn reason_code(&self) -> Option<&'static str> {
        match self {
            ApiError::ForbiddenReason { reason_code, .. } => Some(reason_code),
            _ => None,
        }
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
    #[serde(skip_serializing_if = "Option::is_none")]
    reason_code: Option<&'static str>,
    message: String,
}

impl ErrorResponse {
    fn new(code: u16, reason_code: Option<&'static str>, message: String) -> Self {
        Self {
            error: ErrorPayload {
                code,
                reason_code,
                message,
            },
        }
    }
}
