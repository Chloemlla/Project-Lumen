use super::{
    database_error,
    documents::{
        AdminActionAuditRecord, AdminReleaseRecord, AdminSecurityAllowlistRecord, AdminTemplateRecord,
        EntitlementRecord,
    },
    time::now_millis,
    AppStore,
};
use crate::{
    error::ApiError,
    models::{AdminActionResponse, AdminOperatorDto},
};
use mongodb::{bson::doc, options::ReplaceOptions};
use serde_json::Value;
use uuid::Uuid;

impl AppStore {
    pub async fn record_admin_action(
        &self,
        operator: &AdminOperatorDto,
        action: String,
        payload: Value,
    ) -> Result<AdminActionResponse, ApiError> {
        let recorded_at = now_millis();
        self.apply_admin_action(&action, &payload, recorded_at).await?;
        self.admin_actions
            .insert_one(
                AdminActionAuditRecord {
                    id: Uuid::new_v4().to_string(),
                    operator: operator.username.clone(),
                    action: action.clone(),
                    payload,
                    recorded_at,
                },
                None,
            )
            .await
            .map_err(database_error)?;

        Ok(AdminActionResponse {
            accepted: true,
            action,
            recorded_at,
        })
    }

    async fn apply_admin_action(&self, action: &str, payload: &Value, now: i64) -> Result<(), ApiError> {
        match action {
            "change-plan" => self.apply_change_plan(payload, now).await,
            "revoke-pro" => self.apply_revoke_pro(payload, now).await,
            "push-template" => self.apply_template_push(payload, now).await,
            "force-update" => self.apply_force_update(payload, now).await,
            "save-allowlist" => self.apply_allowlist(payload, now).await,
            _ => Ok(()),
        }
    }

    async fn apply_change_plan(&self, payload: &Value, now: i64) -> Result<(), ApiError> {
        let user_id = payload_string(payload, "userId")?;
        self.entitlements
            .insert_one(
                EntitlementRecord {
                    id: Uuid::new_v4().to_string(),
                    user_id,
                    source: "admin".to_owned(),
                    product_id: payload_str(payload, "productId", "manual_admin_grant"),
                    purchase_token: "",
                    tier: payload_str(payload, "tier", "PRO"),
                    status: "active".to_owned(),
                    purchased_at: now,
                    expires_at: payload.get("expiresAt").and_then(Value::as_i64).unwrap_or_default(),
                    last_verified_at: now,
                    raw_payload_json: payload.to_string(),
                },
                None,
            )
            .await
            .map_err(database_error)?;
        Ok(())
    }

    async fn apply_revoke_pro(&self, payload: &Value, now: i64) -> Result<(), ApiError> {
        let user_id = payload_string(payload, "userId")?;
        self.entitlements
            .update_many(
                doc! { "userId": user_id, "tier": { "$in": ["PRO", "PLUS", "TEAM", "DEVELOPER"] } },
                doc! { "$set": { "status": "revoked", "lastVerifiedAt": now } },
                None,
            )
            .await
            .map_err(database_error)?;
        Ok(())
    }

    async fn apply_template_push(&self, payload: &Value, now: i64) -> Result<(), ApiError> {
        let id = payload
            .get("id")
            .and_then(Value::as_str)
            .map(str::to_owned)
            .unwrap_or_else(|| Uuid::new_v4().to_string());
        let record = AdminTemplateRecord {
            id: id.clone(),
            name: payload_str(payload, "name", "Admin template"),
            tier: payload_str(payload, "tier", "PRO"),
            countdown_style: payload_str(payload, "countdownStyle", "circle"),
            color: payload_str(payload, "color", "#2563EB"),
            locales: payload
                .get("locales")
                .and_then(Value::as_array)
                .map(|items| items.iter().filter_map(Value::as_str).map(str::to_owned).collect())
                .unwrap_or_else(|| vec!["en".to_owned(), "zh".to_owned()]),
            layout_json: payload.get("layoutJson").cloned().unwrap_or_else(|| serde_json::json!({})),
            updated_at: now,
        };
        self.admin_templates
            .replace_one(
                doc! { "_id": &id },
                record,
                ReplaceOptions::builder().upsert(true).build(),
            )
            .await
            .map_err(database_error)?;
        Ok(())
    }

    async fn apply_force_update(&self, payload: &Value, now: i64) -> Result<(), ApiError> {
        let version_code = payload.get("versionCode").and_then(Value::as_i64).unwrap_or_default();
        let id = payload
            .get("id")
            .and_then(Value::as_str)
            .map(str::to_owned)
            .unwrap_or_else(|| format!("version-{version_code}"));
        let record = AdminReleaseRecord {
            id: id.clone(),
            version_code,
            version_name: payload_str(payload, "versionName", "admin-policy"),
            sha256: payload_str(payload, "sha256", "pending"),
            rollout: payload_str(payload, "rollout", "blocked"),
            force_update: payload
                .get("forceUpdate")
                .and_then(Value::as_bool)
                .unwrap_or(true),
            created_at: now,
        };
        self.admin_releases
            .replace_one(
                doc! { "_id": &id },
                record,
                ReplaceOptions::builder().upsert(true).build(),
            )
            .await
            .map_err(database_error)?;
        Ok(())
    }

    async fn apply_allowlist(&self, payload: &Value, now: i64) -> Result<(), ApiError> {
        let origin = payload_string(payload, "origin")?;
        let protocol = payload_str(payload, "protocol", "https");
        let id = format!("{origin}-{protocol}");
        let record = AdminSecurityAllowlistRecord {
            id: id.clone(),
            origin,
            protocol,
            risk: payload_str(payload, "risk", "required"),
            updated_at: now,
        };
        self.admin_security_allowlist
            .replace_one(
                doc! { "_id": &id },
                record,
                ReplaceOptions::builder().upsert(true).build(),
            )
            .await
            .map_err(database_error)?;
        Ok(())
    }
}

fn payload_str(payload: &Value, key: &str, fallback: &str) -> String {
    payload
        .get(key)
        .and_then(Value::as_str)
        .unwrap_or(fallback)
        .to_owned()
}

fn payload_string(payload: &Value, key: &str) -> Result<String, ApiError> {
    payload
        .get(key)
        .and_then(Value::as_str)
        .filter(|value| !value.trim().is_empty())
        .map(str::to_owned)
        .ok_or_else(|| ApiError::BadRequest(format!("{key} is required.")))
}
