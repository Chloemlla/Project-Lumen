use crate::models::{EntitlementDto, SyncChange, UserDto};
use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UserRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub email: String,
    pub created_at: i64,
    pub device_installation_id: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PendingLogin {
    #[serde(rename = "_id")]
    pub request_id: String,
    pub email: String,
    pub code: String,
    pub expires_at: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SessionRecord {
    #[serde(rename = "_id")]
    pub token: String,
    pub user_id: String,
    pub expires_at: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct EntitlementRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub user_id: String,
    pub source: String,
    pub product_id: String,
    pub purchase_token: String,
    pub tier: String,
    pub status: String,
    pub purchased_at: i64,
    pub expires_at: i64,
    pub last_verified_at: i64,
    pub raw_payload_json: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct StoredSyncChange {
    #[serde(rename = "_id")]
    pub id: String,
    pub user_id: String,
    pub cursor: i64,
    pub change: SyncChange,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct BackupRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub user_id: String,
    pub device_installation_id: String,
    pub schema_version: i32,
    pub exported_at: i64,
    pub uploaded_at: i64,
    pub backup: Value,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub(crate) struct CounterRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub value: i64,
}

impl UserRecord {
    pub fn to_dto(&self) -> UserDto {
        UserDto {
            id: self.id.clone(),
            email: self.email.clone(),
            created_at: self.created_at,
            device_installation_id: self.device_installation_id.clone(),
        }
    }
}

impl EntitlementRecord {
    pub(crate) fn to_dto(self) -> EntitlementDto {
        EntitlementDto {
            id: self.id,
            source: self.source,
            product_id: self.product_id,
            purchase_token: self.purchase_token,
            tier: self.tier,
            status: self.status,
            purchased_at: self.purchased_at,
            expires_at: self.expires_at,
            last_verified_at: self.last_verified_at,
            raw_payload_json: self.raw_payload_json,
        }
    }
}
