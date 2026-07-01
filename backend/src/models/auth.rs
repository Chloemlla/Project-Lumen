use crate::models::UserDto;
use serde::{Deserialize, Serialize};

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StartEmailRequest {
    pub email: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StartEmailResponse {
    pub request_id: String,
    pub expires_at: i64,
    pub delivery: String,
    pub dev_code: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VerifyEmailRequest {
    pub email: String,
    pub request_id: String,
    pub code: String,
    pub device_installation_id: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RefreshSessionRequest {
    pub refresh_token: String,
    pub device_installation_id: Option<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AuthSessionResponse {
    pub access_token: String,
    pub refresh_token: String,
    pub token_type: String,
    pub expires_at: i64,
    pub refresh_expires_at: i64,
    pub user: UserDto,
}
