use super::{
    database_error,
    documents::EntitlementRecord,
    time::now_millis,
    AppStore,
};
use crate::{
    error::ApiError,
    models::{EntitlementsResponse, GooglePurchaseVerifyRequest, PurchaseVerifyResponse},
};
use futures_util::TryStreamExt;
use mongodb::{bson::doc, options::FindOptions};
use serde_json::json;
use uuid::Uuid;

impl AppStore {
    pub async fn list_entitlements(&self, user_id: &str) -> Result<EntitlementsResponse, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "purchasedAt": -1, "_id": -1 })
            .build();
        let entitlements: Vec<EntitlementRecord> = self
            .entitlements
            .find(doc! { "userId": user_id }, options)
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)?;
        let tier = resolve_active_tier(&entitlements);
        let entitlement_dtos = entitlements.into_iter().map(EntitlementRecord::to_dto).collect();

        Ok(EntitlementsResponse {
            tier,
            synced_at: now_millis(),
            entitlements: entitlement_dtos,
        })
    }

    pub async fn verify_google_purchase(
        &self,
        user_id: &str,
        request: GooglePurchaseVerifyRequest,
        accept_unverified: bool,
    ) -> Result<PurchaseVerifyResponse, ApiError> {
        if request.product_id.trim().is_empty() || request.purchase_token.trim().is_empty() {
            return Err(ApiError::BadRequest("productId and purchaseToken are required.".to_owned()));
        }

        let now = now_millis();
        let status = if accept_unverified { "active" } else { "pending" };
        let tier = if accept_unverified {
            tier_for_product(&request.product_id)
        } else {
            "FREE".to_owned()
        };
        let entitlement_tier = tier_for_product(&request.product_id);
        let raw_payload_json = json!({
            "deviceInstallationId": request.device_installation_id.unwrap_or_default(),
            "verificationMode": if accept_unverified { "unverified_accepted" } else { "pending_server_verification" }
        })
        .to_string();

        let record = EntitlementRecord {
            id: Uuid::new_v4().to_string(),
            user_id: user_id.to_owned(),
            source: "google_play".to_owned(),
            product_id: request.product_id,
            purchase_token: request.purchase_token,
            tier: entitlement_tier,
            status: status.to_owned(),
            purchased_at: now,
            expires_at: 0,
            last_verified_at: now,
            raw_payload_json,
        };
        let dto = record.clone().to_dto();

        self.entitlements.insert_one(record, None).await.map_err(database_error)?;

        Ok(PurchaseVerifyResponse {
            status: status.to_owned(),
            tier,
            verified_at: now,
            entitlement: Some(dto),
        })
    }
}

fn resolve_active_tier(entitlements: &[EntitlementRecord]) -> String {
    entitlements
        .iter()
        .filter(|entitlement| entitlement.status == "active")
        .map(|entitlement| entitlement.tier.as_str())
        .max_by_key(|tier| tier_rank(*tier))
        .unwrap_or("FREE")
        .to_owned()
}

fn tier_for_product(product_id: &str) -> String {
    let normalized = product_id.to_ascii_lowercase();
    if normalized.contains("team") {
        "TEAM"
    } else if normalized.contains("plus") || normalized.contains("monthly") || normalized.contains("yearly") {
        "PLUS"
    } else if normalized.contains("pro") {
        "PRO"
    } else {
        "FREE"
    }
    .to_owned()
}

fn tier_rank(tier: &str) -> u8 {
    match tier {
        "TEAM" => 3,
        "PLUS" => 2,
        "PRO" => 1,
        _ => 0,
    }
}
