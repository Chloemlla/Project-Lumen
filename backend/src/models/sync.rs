use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncChange {
    pub collection: String,
    pub remote_id: String,
    pub operation: String,
    pub payload: Value,
    pub updated_at: i64,
    pub deleted_at: i64,
    pub device_installation_id: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncPushRequest {
    pub device_installation_id: String,
    pub cursor: Option<u64>,
    pub changes: Vec<SyncChange>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncPushResponse {
    pub accepted: usize,
    pub next_cursor: u64,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncChangesQuery {
    pub since: Option<u64>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncChangesResponse {
    pub changes: Vec<SyncChange>,
    pub next_cursor: u64,
    pub server_time: i64,
}
