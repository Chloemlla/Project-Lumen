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
    #[serde(default)]
    pub device_asset_model: String,
    #[serde(default)]
    pub device_asset_version_code: i64,
    #[serde(default)]
    pub device_asset_last_seen_at: i64,
    #[serde(default)]
    pub device_asset_security_config: String,
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
    #[serde(default)]
    pub refresh_token: String,
    pub user_id: String,
    pub expires_at: i64,
    #[serde(default)]
    pub refresh_expires_at: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ApiNonceRecord {
    #[serde(rename = "_id")]
    pub nonce: String,
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

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct AdminSessionRecord {
    #[serde(rename = "_id")]
    pub access_token: String,
    pub refresh_token: String,
    pub username: String,
    pub role: String,
    pub expires_at: i64,
    pub refresh_expires_at: i64,
    pub created_at: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct AdminActionAuditRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub operator: String,
    pub action: String,
    pub payload: Value,
    pub recorded_at: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct AdminAccessAuditRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub at: i64,
    pub user_id: String,
    pub endpoint: String,
    pub ip: String,
    pub geo: String,
    pub status: u16,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct AdminCrashReportRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub group_key: String,
    pub version_code: i64,
    pub count: i64,
    pub affected_users: i64,
    pub risk: String,
    pub clean_stack: Vec<String>,
    pub last_seen_at: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct AdminApiMetricRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub endpoint: String,
    pub qps: f64,
    pub p95_ms: i64,
    pub status_2xx: i64,
    pub status_4xx: i64,
    pub status_5xx: i64,
    pub sampled_at: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct AdminSyncMetricRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub endpoint: String,
    pub average_payload_kb: i64,
    pub largest_payload_kb: i64,
    pub p95_ms: i64,
    pub rejected_payloads: i64,
    pub sampled_at: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct AdminTemplateRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub name: String,
    pub tier: String,
    pub countdown_style: String,
    pub color: String,
    pub locales: Vec<String>,
    pub layout_json: Value,
    pub updated_at: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct AdminTelemetryRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub label: String,
    pub value: f64,
    pub range_days: i64,
    pub sampled_at: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct AdminReleaseRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub version_code: i64,
    pub version_name: String,
    pub sha256: String,
    pub rollout: String,
    pub force_update: bool,
    pub created_at: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct AdminSecurityAllowlistRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub origin: String,
    pub protocol: String,
    pub risk: String,
    pub updated_at: i64,
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
