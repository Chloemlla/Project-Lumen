use super::{
    database_error,
    documents::{
        AdminActionAuditRecord, AdminReleaseAssetRecord, AdminReleasePatchRecord,
        AdminReleaseRecord, AdminSecurityAllowlistRecord, AdminTemplateRecord, EntitlementRecord,
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
        self.apply_admin_action(&action, &payload, recorded_at)
            .await?;
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

    async fn apply_admin_action(
        &self,
        action: &str,
        payload: &Value,
        now: i64,
    ) -> Result<(), ApiError> {
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
                    purchase_token: String::new(),
                    tier: payload_str(payload, "tier", "PRO"),
                    status: "active".to_owned(),
                    purchased_at: now,
                    expires_at: payload
                        .get("expiresAt")
                        .and_then(Value::as_i64)
                        .unwrap_or_default(),
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
                .map(|items| {
                    items
                        .iter()
                        .filter_map(Value::as_str)
                        .map(str::to_owned)
                        .collect()
                })
                .unwrap_or_else(|| vec!["en".to_owned(), "zh".to_owned()]),
            layout_json: payload
                .get("layoutJson")
                .cloned()
                .unwrap_or_else(|| serde_json::json!({})),
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
        let version_code = payload
            .get("versionCode")
            .and_then(Value::as_i64)
            .unwrap_or_default();
        let id = payload
            .get("id")
            .and_then(Value::as_str)
            .map(str::to_owned)
            .unwrap_or_else(|| format!("version-{version_code}"));
        let assets = release_assets_from_payload(payload)?;
        let patches = release_patches_from_payload(payload)?;
        let legacy_sha256 = payload
            .get("sha256")
            .and_then(Value::as_str)
            .map(str::to_owned)
            .or_else(|| assets.first().map(|asset| asset.sha256.clone()))
            .unwrap_or_else(|| "pending".to_owned());
        let record = AdminReleaseRecord {
            id: id.clone(),
            version_code,
            version_name: payload_str(payload, "versionName", "admin-policy"),
            channel: payload_str(payload, "channel", "stable"),
            release_url: payload_str(payload, "releaseUrl", ""),
            sha256: legacy_sha256,
            assets,
            patches,
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

fn release_assets_from_payload(payload: &Value) -> Result<Vec<AdminReleaseAssetRecord>, ApiError> {
    let mut assets = Vec::new();
    if let Some(items) = payload.get("assets").and_then(Value::as_array) {
        for item in items {
            if let Some(asset) = release_asset_from_value(item)? {
                assets.push(asset);
            }
        }
    }

    let full_apk_url = payload_str(payload, "fullApkUrl", "");
    let full_apk_sha256 = payload_str(payload, "fullApkSha256", "");
    if !full_apk_url.is_empty() || !full_apk_sha256.is_empty() {
        let asset = release_asset_from_fields(
            payload_str(payload, "abi", "universal"),
            payload_str(payload, "name", "Project-Lumen_android_universal.apk"),
            full_apk_url,
            full_apk_sha256,
            payload
                .get("fullApkSizeBytes")
                .and_then(Value::as_i64)
                .unwrap_or_default(),
            payload_str(
                payload,
                "contentType",
                "application/vnd.android.package-archive",
            ),
        )?;
        assets.push(asset);
    }

    assets.sort_by(|left, right| left.abi.cmp(&right.abi).then(left.name.cmp(&right.name)));
    assets.dedup_by(|left, right| {
        left.abi.eq_ignore_ascii_case(&right.abi) && left.url.eq_ignore_ascii_case(&right.url)
    });
    Ok(assets)
}

fn release_asset_from_value(value: &Value) -> Result<Option<AdminReleaseAssetRecord>, ApiError> {
    let url = value
        .get("url")
        .or_else(|| value.get("downloadUrl"))
        .or_else(|| value.get("fullApkUrl"))
        .and_then(Value::as_str)
        .unwrap_or_default()
        .trim()
        .to_owned();
    let sha256 = value
        .get("sha256")
        .or_else(|| value.get("fullApkSha256"))
        .and_then(Value::as_str)
        .unwrap_or_default()
        .trim()
        .to_owned();
    if url.is_empty() && sha256.is_empty() {
        return Ok(None);
    }
    Ok(Some(release_asset_from_fields(
        value
            .get("abi")
            .and_then(Value::as_str)
            .unwrap_or("universal")
            .to_owned(),
        value
            .get("name")
            .and_then(Value::as_str)
            .map(str::to_owned)
            .unwrap_or_else(|| file_name_from_url(&url)),
        url,
        sha256,
        value
            .get("sizeBytes")
            .or_else(|| value.get("fullApkSizeBytes"))
            .and_then(Value::as_i64)
            .unwrap_or_default(),
        value
            .get("contentType")
            .and_then(Value::as_str)
            .unwrap_or("application/vnd.android.package-archive")
            .to_owned(),
    )?))
}

fn release_asset_from_fields(
    abi: String,
    name: String,
    url: String,
    sha256: String,
    size_bytes: i64,
    content_type: String,
) -> Result<AdminReleaseAssetRecord, ApiError> {
    if !is_github_release_download_url(&url) {
        return Err(ApiError::BadRequest(
            "Release asset URLs must point to GitHub release downloads.".to_owned(),
        ));
    }
    if !is_sha256_hex(&sha256) {
        return Err(ApiError::BadRequest(
            "Release asset SHA256 must be a 64-character hex string.".to_owned(),
        ));
    }
    Ok(AdminReleaseAssetRecord {
        abi: abi.trim().to_owned().if_empty("universal"),
        name: name
            .trim()
            .to_owned()
            .if_empty("Project-Lumen_android_universal.apk"),
        url,
        sha256: sha256.to_ascii_lowercase(),
        size_bytes: size_bytes.max(0),
        content_type,
    })
}

fn release_patches_from_payload(payload: &Value) -> Result<Vec<AdminReleasePatchRecord>, ApiError> {
    let mut patches = Vec::new();
    if let Some(items) = payload.get("patches").and_then(Value::as_array) {
        for item in items {
            let patch_url = payload_nested_str(item, "patchUrl");
            let patch_sha256 = payload_nested_str(item, "patchSha256");
            if patch_url.is_empty() && patch_sha256.is_empty() {
                continue;
            }
            if !is_github_release_download_url(&patch_url) {
                return Err(ApiError::BadRequest(
                    "Patch URLs must point to GitHub release downloads.".to_owned(),
                ));
            }
            if !is_sha256_hex(&patch_sha256) {
                return Err(ApiError::BadRequest(
                    "Patch SHA256 must be a 64-character hex string.".to_owned(),
                ));
            }
            patches.push(AdminReleasePatchRecord {
                from_version_code: item
                    .get("fromVersionCode")
                    .and_then(Value::as_i64)
                    .unwrap_or_default(),
                from_sha256: payload_nested_str(item, "fromSha256").to_ascii_lowercase(),
                to_sha256: payload_nested_str(item, "toSha256").to_ascii_lowercase(),
                patch_url,
                patch_sha256: patch_sha256.to_ascii_lowercase(),
                algorithm: payload_nested_str(item, "algorithm").if_empty("bsdiff"),
                size_bytes: item
                    .get("sizeBytes")
                    .and_then(Value::as_i64)
                    .unwrap_or_default(),
            });
        }
    }
    Ok(patches)
}

fn payload_nested_str(payload: &Value, key: &str) -> String {
    payload
        .get(key)
        .and_then(Value::as_str)
        .unwrap_or_default()
        .trim()
        .to_owned()
}

fn file_name_from_url(url: &str) -> String {
    url.rsplit('/')
        .next()
        .filter(|value| !value.trim().is_empty())
        .unwrap_or("Project-Lumen_android_universal.apk")
        .to_owned()
}

fn is_github_release_download_url(url: &str) -> bool {
    let value = url.to_ascii_lowercase();
    value.starts_with("https://github.com/") && value.contains("/releases/download/")
}

fn is_sha256_hex(value: &str) -> bool {
    value.len() == 64 && value.bytes().all(|byte| byte.is_ascii_hexdigit())
}

trait EmptyFallback {
    fn if_empty(self, fallback: &str) -> String;
}

impl EmptyFallback for String {
    fn if_empty(self, fallback: &str) -> String {
        if self.trim().is_empty() {
            fallback.to_owned()
        } else {
            self
        }
    }
}
