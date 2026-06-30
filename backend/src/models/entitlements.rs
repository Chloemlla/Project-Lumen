use serde::{Deserialize, Serialize};

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct EntitlementDto {
    pub id: String,
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

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct EntitlementsResponse {
    pub tier: String,
    pub synced_at: i64,
    pub entitlements: Vec<EntitlementDto>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GooglePurchaseVerifyRequest {
    pub product_id: String,
    pub purchase_token: String,
    pub device_installation_id: Option<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PurchaseVerifyResponse {
    pub status: String,
    pub tier: String,
    pub verified_at: i64,
    pub entitlement: Option<EntitlementDto>,
}
