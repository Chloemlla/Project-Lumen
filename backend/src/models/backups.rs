use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BackupUploadRequest {
    pub device_installation_id: String,
    pub schema_version: i32,
    pub exported_at: i64,
    pub backup: Value,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BackupMetadata {
    pub id: String,
    pub device_installation_id: String,
    pub schema_version: i32,
    pub exported_at: i64,
    pub uploaded_at: i64,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BackupDocument {
    pub metadata: BackupMetadata,
    pub backup: Value,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LatestBackupResponse {
    pub backup: Option<BackupDocument>,
}
