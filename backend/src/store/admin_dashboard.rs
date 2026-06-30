use super::{database_error, documents::*, time::now_millis, AppStore};
use crate::{
    error::ApiError,
    models::{
        AdminAccessAuditEntry, AdminBackupSnapshot, AdminBackupSummary, AdminContentSection,
        AdminDashboardResponse, AdminDeviceAsset, AdminEntitlementItem, AdminObservabilitySection,
        AdminPurchaseAuditEntry, AdminReleaseSection, AdminRouteStatusItem, AdminUserProfile,
        AdminUsersSection,
    },
};
use futures_util::TryStreamExt;
use mongodb::{bson::doc, options::FindOptions};
use serde_json::Value;
use std::collections::HashMap;

impl AppStore {
    pub async fn admin_dashboard_snapshot(&self) -> Result<AdminDashboardResponse, ApiError> {
        let users = self.recent_users().await?;
        let latest_sync_by_user = self.latest_sync_by_user().await?;
        let entitlements = self.admin_entitlements().await?;
        let tier_by_user = tier_by_user(&entitlements);
        let profiles = users
            .iter()
            .map(|user| AdminUserProfile {
                id: user.id.clone(),
                email: user.email.clone(),
                registered_at: user.created_at,
                last_sync_at: latest_sync_by_user.get(&user.id).copied().unwrap_or_default(),
                plan_tier: tier_by_user.get(&user.id).cloned().unwrap_or_else(|| "FREE".to_owned()),
                feature_flags: Vec::new(),
            })
            .collect();

        Ok(AdminDashboardResponse {
            generated_at: now_millis(),
            users: AdminUsersSection {
                profiles,
                devices: self.device_assets(&users).await?,
                access_audit: self.access_audit().await?,
                entitlements,
                purchase_audit: self.purchase_audit().await?,
                backups: self.backup_snapshots().await?,
            },
            observability: AdminObservabilitySection {
                crash_groups: self.crash_groups().await?,
                clean_stack: self.latest_clean_stack().await?,
                api_metrics: self.api_metrics().await?,
                sync_metrics: self.sync_metrics().await?,
            },
            content: AdminContentSection {
                templates: self.template_catalog().await?,
                telemetry: self.telemetry().await?,
            },
            release: AdminReleaseSection {
                releases: self.releases().await?,
                routes: route_status(),
                allowlist: self.security_allowlist().await?,
            },
        })
    }

    async fn recent_users(&self) -> Result<Vec<UserRecord>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "createdAt": -1 })
            .limit(25)
            .build();
        self.users
            .find(doc! {}, options)
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)
    }

    async fn latest_sync_by_user(&self) -> Result<HashMap<String, i64>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "cursor": -1 })
            .limit(200)
            .build();
        let changes: Vec<StoredSyncChange> = self
            .sync_changes
            .find(doc! {}, options)
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)?;
        let mut latest: HashMap<String, i64> = HashMap::new();
        for change in changes {
            latest
                .entry(change.user_id)
                .and_modify(|value| *value = (*value).max(change.change.updated_at))
                .or_insert(change.change.updated_at);
        }
        Ok(latest)
    }

    async fn device_assets(&self, users: &[UserRecord]) -> Result<Vec<AdminDeviceAsset>, ApiError> {
        Ok(users
            .iter()
            .filter(|user| !user.device_installation_id.is_empty())
            .map(|user| AdminDeviceAsset {
                user_id: user.id.clone(),
                device_installation_id: user.device_installation_id.clone(),
                model: "Android device".to_owned(),
                version_code: 0,
                last_seen_at: user.created_at,
                local_security_config: "Reported by future admin access audit events".to_owned(),
            })
            .collect())
    }

    async fn access_audit(&self) -> Result<Vec<AdminAccessAuditEntry>, ApiError> {
        let options = FindOptions::builder().sort(doc! { "at": -1 }).limit(25).build();
        let entries: Vec<AdminAccessAuditRecord> = self
            .admin_access_audit
            .find(doc! {}, options)
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)?;
        Ok(entries
            .into_iter()
            .map(|entry| AdminAccessAuditEntry {
                at: entry.at,
                user_id: entry.user_id,
                endpoint: entry.endpoint,
                ip: entry.ip,
                geo: entry.geo,
                status: entry.status,
            })
            .collect())
    }

    async fn admin_entitlements(&self) -> Result<Vec<AdminEntitlementItem>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "purchasedAt": -1 })
            .limit(50)
            .build();
        let rows: Vec<EntitlementRecord> = self
            .entitlements
            .find(doc! {}, options)
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)?;
        Ok(rows
            .into_iter()
            .map(|row| AdminEntitlementItem {
                user_id: row.user_id,
                product_id: row.product_id,
                tier: row.tier,
                status: row.status,
                expires_at: row.expires_at,
                last_verified_at: row.last_verified_at,
            })
            .collect())
    }

    async fn purchase_audit(&self) -> Result<Vec<AdminPurchaseAuditEntry>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "lastVerifiedAt": -1 })
            .limit(25)
            .build();
        let rows: Vec<EntitlementRecord> = self
            .entitlements
            .find(doc! { "source": "google_play" }, options)
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)?;
        Ok(rows
            .into_iter()
            .map(|row| AdminPurchaseAuditEntry {
                at: row.last_verified_at,
                user_id: row.user_id,
                product_id: row.product_id,
                purchase_token: mask_token(&row.purchase_token),
                status: row.status,
                action: "server verification record".to_owned(),
            })
            .collect())
    }

    async fn backup_snapshots(&self) -> Result<Vec<AdminBackupSnapshot>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "uploadedAt": -1 })
            .limit(25)
            .build();
        let rows: Vec<BackupRecord> = self
            .backups
            .find(doc! {}, options)
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)?;
        Ok(rows
            .into_iter()
            .map(|row| AdminBackupSnapshot {
                id: row.id,
                user_id: row.user_id,
                uploaded_at: row.uploaded_at,
                schema_version: row.schema_version,
                summary: backup_summary(&row.backup),
            })
            .collect())
    }

}

fn backup_summary(backup: &Value) -> AdminBackupSummary {
    AdminBackupSummary {
        templates: json_array_len(backup, "templates"),
        eye_stat_days: json_array_len(backup, "dailyEyeStats"),
        pomodoro_days: json_array_len(backup, "dailyPomodoroStats"),
        reminder_plans: json_array_len(backup, "reminderPlans"),
        entitlements: json_array_len(backup, "entitlements"),
    }
}

fn json_array_len(value: &Value, key: &str) -> usize {
    value
        .get(key)
        .and_then(Value::as_array)
        .map(Vec::len)
        .unwrap_or_default()
}

fn tier_by_user(entitlements: &[AdminEntitlementItem]) -> HashMap<String, String> {
    let mut tiers = HashMap::new();
    for entitlement in entitlements {
        if entitlement.status != "active" {
            continue;
        }
        tiers
            .entry(entitlement.user_id.clone())
            .and_modify(|tier| {
                if tier_rank(&entitlement.tier) > tier_rank(tier) {
                    *tier = entitlement.tier.clone();
                }
            })
            .or_insert_with(|| entitlement.tier.clone());
    }
    tiers
}

fn tier_rank(tier: &str) -> u8 {
    match tier {
        "DEVELOPER" => 5,
        "TEAM" => 4,
        "PLUS" => 3,
        "PRO" => 2,
        _ => 1,
    }
}

fn mask_token(token: &str) -> String {
    if token.len() <= 8 {
        return "****".to_owned();
    }
    format!("{}...{}", &token[..4], &token[token.len() - 4..])
}

fn route_status() -> Vec<AdminRouteStatusItem> {
    vec![
        AdminRouteStatusItem {
            module: "routes/session.rs".to_owned(),
            path: "/api/v1/auth/email/*".to_owned(),
            state: "ok".to_owned(),
            p95_ms: 0,
        },
        AdminRouteStatusItem {
            module: "routes/sync.rs".to_owned(),
            path: "/api/v1/sync/*".to_owned(),
            state: "ok".to_owned(),
            p95_ms: 0,
        },
        AdminRouteStatusItem {
            module: "routes/backups.rs".to_owned(),
            path: "/api/v1/backups/*".to_owned(),
            state: "ok".to_owned(),
            p95_ms: 0,
        },
        AdminRouteStatusItem {
            module: "routes/admin.rs".to_owned(),
            path: "/api/admin/*".to_owned(),
            state: "ok".to_owned(),
            p95_ms: 0,
        },
    ]
}
