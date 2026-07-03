use serde::{Deserialize, Serialize};

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct HealthResponse {
    pub status: String,
    pub service: String,
    pub version: String,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UserDto {
    pub id: String,
    pub email: String,
    pub created_at: i64,
    pub device_installation_id: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MeResponse {
    pub user: UserDto,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceRegistrationRequest {
    pub device_installation_id: String,
    #[serde(default)]
    pub device_fingerprint: String,
    pub model: String,
    pub version_code: i64,
    pub local_security_config: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceRegistrationResponse {
    pub accepted: bool,
    pub device_installation_id: String,
    pub device_fingerprint: String,
    pub registered_at: i64,
}
