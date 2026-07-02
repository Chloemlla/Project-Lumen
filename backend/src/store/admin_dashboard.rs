use super::{
    database_error, documents::*, telemetry::TelemetryUploadRecord, time::now_millis, AppStore,
};
use crate::{
    error::ApiError,
    models::{
        AdminAccessAuditEntry, AdminApiMetric, AdminBackupSnapshot, AdminBackupSummary,
        AdminContentSection, AdminDashboardResponse, AdminDeviceAsset, AdminEntitlementItem,
        AdminObservabilitySection, AdminPurchaseAuditEntry, AdminReleaseSection,
        AdminRouteStatusItem, AdminUserProfile, AdminUsersSection, DeviceDiagnosticsTelemetry,
        DeviceProfileTelemetry,
    },
};
use futures_util::TryStreamExt;
use mongodb::{
    bson::{doc, Bson},
    options::FindOptions,
};
use serde_json::Value;
use std::collections::{HashMap, HashSet};

impl AppStore {
    pub async fn admin_dashboard_snapshot(&self) -> Result<AdminDashboardResponse, ApiError> {
        let users = self.recent_users().await?;
        let user_ids = users.iter().map(|user| user.id.clone()).collect::<Vec<_>>();
        let (latest_sync_by_user, entitlements) =
            tokio::try_join!(self.latest_sync_by_user(&user_ids), self.admin_entitlements(),)?;
        let tier_by_user = tier_by_user(&entitlements);
        let profiles = users
            .iter()
            .map(|user| AdminUserProfile {
                id: user.id.clone(),
                email: user.email.clone(),
                registered_at: user.created_at,
                last_sync_at: latest_sync_by_user
                    .get(&user.id)
                    .copied()
                    .unwrap_or_default(),
                plan_tier: tier_by_user
                    .get(&user.id)
                    .cloned()
                    .unwrap_or_else(|| "FREE".to_owned()),
                feature_flags: Vec::new(),
            })
            .collect();
        let (
            access_audit,
            purchase_audit,
            backups,
            crash_groups,
            api_metrics,
            sync_metrics,
            templates,
            telemetry,
            releases,
            allowlist,
            devices,
        ) = tokio::try_join!(
            self.access_audit(),
            self.purchase_audit(),
            self.backup_snapshots(),
            self.crash_groups(),
            self.api_metrics(),
            self.sync_metrics(),
            self.template_catalog(),
            self.telemetry(),
            self.releases(),
            self.security_allowlist(),
            self.device_assets(&users),
        )?;
        let clean_stack = crash_groups
            .first()
            .map(|group| group.clean_stack.clone())
            .unwrap_or_default();
        let routes = route_status(&api_metrics);

        Ok(AdminDashboardResponse {
            generated_at: now_millis(),
            users: AdminUsersSection {
                profiles,
                devices,
                access_audit,
                entitlements,
                purchase_audit,
                backups,
            },
            observability: AdminObservabilitySection {
                crash_groups,
                clean_stack,
                api_metrics,
                sync_metrics,
            },
            content: AdminContentSection {
                templates,
                telemetry,
            },
            release: AdminReleaseSection {
                releases,
                routes,
                allowlist,
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

    async fn latest_sync_by_user(
        &self,
        user_ids: &[String],
    ) -> Result<HashMap<String, i64>, ApiError> {
        if user_ids.is_empty() {
            return Ok(HashMap::new());
        }
        let pipeline = vec![
            doc! { "$match": { "userId": { "$in": user_ids.to_vec() } } },
            doc! {
                "$group": {
                    "_id": "$userId",
                    "lastSyncAt": { "$max": "$change.updatedAt" },
                },
            },
        ];
        let mut cursor = self
            .sync_changes
            .aggregate(pipeline, None)
            .await
            .map_err(database_error)?;
        let mut latest: HashMap<String, i64> = HashMap::new();
        while let Some(row) = cursor.try_next().await.map_err(database_error)? {
            if let Ok(user_id) = row.get_str("_id") {
                latest.insert(user_id.to_owned(), bson_i64(row.get("lastSyncAt")));
            }
        }
        Ok(latest)
    }

    async fn access_audit(&self) -> Result<Vec<AdminAccessAuditEntry>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "at": -1 })
            .limit(25)
            .build();
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

    async fn device_assets(&self, users: &[UserRecord]) -> Result<Vec<AdminDeviceAsset>, ApiError> {
        let mut devices_by_id: HashMap<String, AdminDeviceAsset> = HashMap::new();
        for user in users {
            let device_installation_id = user.device_installation_id.trim();
            if device_installation_id.is_empty() {
                continue;
            }
            upsert_latest_device(
                &mut devices_by_id,
                AdminDeviceAsset {
                    user_id: user.id.clone(),
                    device_installation_id: device_installation_id.to_owned(),
                    model: non_empty_or(user.device_asset_model.trim(), "not reported").to_owned(),
                    version_code: user.device_asset_version_code.max(0),
                    last_seen_at: user.device_asset_last_seen_at.max(user.created_at),
                    local_security_config: non_empty_or(
                        user.device_asset_security_config.trim(),
                        "not reported",
                    )
                    .to_owned(),
                },
            );
        }

        let options = FindOptions::builder()
            .sort(doc! { "receivedAt": -1 })
            .limit(200)
            .build();
        let telemetry_rows: Vec<TelemetryUploadRecord> = self
            .telemetry_uploads
            .find(doc! { "deviceInstallationId": { "$ne": "" } }, options)
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)?;

        for row in telemetry_rows {
            let device_installation_id = row.device_installation_id.trim().to_owned();
            if device_installation_id.is_empty() {
                continue;
            }
            upsert_latest_device(
                &mut devices_by_id,
                AdminDeviceAsset {
                    user_id: row.user_id,
                    device_installation_id,
                    model: device_model(&row.payload.device_profile),
                    version_code: row.payload.device_profile.app_version_code,
                    last_seen_at: row.received_at,
                    local_security_config: device_security_summary(
                        row.payload.device_diagnostics.as_ref(),
                    ),
                },
            );
        }

        let options = FindOptions::builder()
            .sort(doc! { "uploadedAt": -1 })
            .limit(200)
            .build();
        let backup_rows: Vec<BackupRecord> = self
            .backups
            .find(doc! { "deviceInstallationId": { "$ne": "" } }, options)
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)?;
        for row in backup_rows {
            let device_installation_id = row.device_installation_id.trim().to_owned();
            if device_installation_id.is_empty() {
                continue;
            }
            upsert_latest_device(
                &mut devices_by_id,
                AdminDeviceAsset {
                    user_id: row.user_id,
                    device_installation_id,
                    model: "not reported".to_owned(),
                    version_code: 0,
                    last_seen_at: row.uploaded_at,
                    local_security_config: "not reported".to_owned(),
                },
            );
        }

        let mut devices = devices_by_id.into_values().collect::<Vec<_>>();
        devices.sort_by(|left, right| right.last_seen_at.cmp(&left.last_seen_at));
        devices.truncate(25);
        Ok(devices)
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

fn upsert_latest_device(
    devices_by_id: &mut HashMap<String, AdminDeviceAsset>,
    device: AdminDeviceAsset,
) {
    match devices_by_id.get(&device.device_installation_id) {
        Some(current) if current.last_seen_at >= device.last_seen_at => {}
        _ => {
            devices_by_id.insert(device.device_installation_id.clone(), device);
        }
    }
}

fn bson_i64(value: Option<&Bson>) -> i64 {
    match value {
        Some(Bson::Int32(value)) => i64::from(*value),
        Some(Bson::Int64(value)) => *value,
        Some(Bson::Double(value)) => *value as i64,
        _ => 0,
    }
}

fn non_empty_or<'a>(value: &'a str, fallback: &'a str) -> &'a str {
    if value.is_empty() {
        fallback
    } else {
        value
    }
}

fn device_model(profile: &DeviceProfileTelemetry) -> String {
    let manufacturer = profile.manufacturer.trim();
    let model = profile.model.trim();
    match (manufacturer.is_empty(), model.is_empty()) {
        (true, true) => "not reported".to_owned(),
        (true, false) => model.to_owned(),
        (false, true) => manufacturer.to_owned(),
        (false, false) if model.starts_with(manufacturer) => model.to_owned(),
        (false, false) => format!("{manufacturer} {model}"),
    }
}

fn device_security_summary(diagnostics: Option<&DeviceDiagnosticsTelemetry>) -> String {
    match diagnostics {
        Some(diagnostics) if diagnostics.shizuku_ready => {
            format!("shizuku ready; {} user apps", diagnostics.user_app_count.max(0))
        }
        Some(diagnostics) => format!(
            "diagnostics collected; shizuku unavailable; {} user apps",
            diagnostics.user_app_count.max(0)
        ),
        None => "not reported".to_owned(),
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
    let mut tiers: HashMap<String, String> = HashMap::new();
    for entitlement in entitlements {
        if entitlement.status != "active" {
            continue;
        }
        tiers
            .entry(entitlement.user_id.clone())
            .and_modify(|tier| {
                if tier_rank(&entitlement.tier) > tier_rank(tier.as_str()) {
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

fn route_status(metrics: &[AdminApiMetric]) -> Vec<AdminRouteStatusItem> {
    let mut seen = HashSet::new();
    let mut routes = Vec::new();
    for metric in metrics {
        let path = metric.endpoint.trim();
        if path.is_empty() || !seen.insert(path.to_owned()) {
            continue;
        }
        routes.push(AdminRouteStatusItem {
            module: route_module(path).to_owned(),
            path: path.to_owned(),
            state: route_state(metric).to_owned(),
            p95_ms: metric.p95_ms,
        });
    }
    routes
}

fn route_module(path: &str) -> &'static str {
    if path.contains("/auth/") {
        "routes/session.rs"
    } else if path.contains("/sync/") {
        "routes/sync.rs"
    } else if path.contains("/backups") {
        "routes/backups.rs"
    } else if path.contains("/purchases/") {
        "routes/purchases.rs"
    } else if path.contains("/telemetry") {
        "routes/telemetry.rs"
    } else if path.contains("/face-analysis/") {
        "routes/face_analysis.rs"
    } else if path.contains("/admin/") {
        "routes/admin.rs"
    } else if path.contains("/entitlements") {
        "routes/entitlements.rs"
    } else if path.contains("/me") {
        "routes/me.rs"
    } else {
        "unknown"
    }
}

fn route_state(metric: &AdminApiMetric) -> &'static str {
    if metric.status_5xx > 0 || metric.p95_ms >= 1_000 {
        "risk"
    } else if metric.status_4xx > metric.status_2xx || metric.p95_ms >= 300 {
        "watch"
    } else {
        "ok"
    }
}
