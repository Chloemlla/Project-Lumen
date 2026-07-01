use super::{database_error, time::now_millis, AppStore};
use crate::{
    error::ApiError,
    models::{
        AdminApiMetric, AdminCrashGroup, AdminReleaseItem, AdminSecurityAllowlistItem,
        AdminSyncMetric, AdminTelemetryItem, AdminTemplateItem,
    },
};
use futures_util::TryStreamExt;
use mongodb::{
    bson::{doc, Bson},
    options::FindOptions,
};

impl AppStore {
    pub(crate) async fn crash_groups(&self) -> Result<Vec<AdminCrashGroup>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "lastSeenAt": -1 })
            .limit(25)
            .build();
        self.admin_crash_reports
            .find(doc! {}, options)
            .await
            .map_err(database_error)?
            .map_ok(|row| AdminCrashGroup {
                group_key: row.group_key,
                version_code: row.version_code,
                count: row.count,
                affected_users: row.affected_users,
                risk: row.risk,
                clean_stack: row.clean_stack,
            })
            .try_collect()
            .await
            .map_err(database_error)
    }

    pub(crate) async fn latest_clean_stack(&self) -> Result<Vec<String>, ApiError> {
        Ok(self
            .crash_groups()
            .await?
            .into_iter()
            .next()
            .map(|group| group.clean_stack)
            .unwrap_or_default())
    }

    pub(crate) async fn api_metrics(&self) -> Result<Vec<AdminApiMetric>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "sampledAt": -1 })
            .limit(25)
            .build();
        self.admin_api_metrics
            .find(doc! {}, options)
            .await
            .map_err(database_error)?
            .map_ok(|row| AdminApiMetric {
                endpoint: row.endpoint,
                qps: row.qps,
                p95_ms: row.p95_ms,
                status_2xx: row.status_2xx,
                status_4xx: row.status_4xx,
                status_5xx: row.status_5xx,
                sampled_at: row.sampled_at,
            })
            .try_collect()
            .await
            .map_err(database_error)
    }

    pub(crate) async fn sync_metrics(&self) -> Result<Vec<AdminSyncMetric>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "sampledAt": -1 })
            .limit(25)
            .build();
        self.admin_sync_metrics
            .find(doc! {}, options)
            .await
            .map_err(database_error)?
            .map_ok(|row| AdminSyncMetric {
                endpoint: row.endpoint,
                average_payload_kb: row.average_payload_kb,
                largest_payload_kb: row.largest_payload_kb,
                p95_ms: row.p95_ms,
                rejected_payloads: row.rejected_payloads,
                sampled_at: row.sampled_at,
            })
            .try_collect()
            .await
            .map_err(database_error)
    }

    pub(crate) async fn template_catalog(&self) -> Result<Vec<AdminTemplateItem>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "updatedAt": -1 })
            .build();
        self.admin_templates
            .find(doc! {}, options)
            .await
            .map_err(database_error)?
            .map_ok(|row| AdminTemplateItem {
                id: row.id,
                name: row.name,
                tier: row.tier,
                countdown_style: row.countdown_style,
                color: row.color,
                locales: row.locales,
                layout_json: row.layout_json,
            })
            .try_collect()
            .await
            .map_err(database_error)
    }

    pub(crate) async fn telemetry(&self) -> Result<Vec<AdminTelemetryItem>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "sampledAt": -1 })
            .limit(25)
            .build();
        let mut items: Vec<AdminTelemetryItem> = self
            .admin_telemetry
            .find(doc! {}, options)
            .await
            .map_err(database_error)?
            .map_ok(|row| AdminTelemetryItem {
                label: row.label,
                value: row.value,
                range_days: row.range_days,
                sampled_at: row.sampled_at,
            })
            .try_collect()
            .await
            .map_err(database_error)?;

        let now = now_millis();
        let since = now - 7 * 24 * 60 * 60 * 1000;
        let pipeline = vec![
            doc! {
                "$match": {
                    "receivedAt": { "$gte": since },
                    "payload.sourceApp": {
                        "$exists": true,
                        "$nin": ["project_lumen", ""],
                    },
                }
            },
            doc! {
                "$group": {
                    "_id": "$payload.sourceApp",
                    "count": { "$sum": 1 },
                }
            },
            doc! { "$sort": { "count": -1 } },
            doc! { "$limit": 10 },
        ];
        let mut cursor = self
            .telemetry_uploads
            .aggregate(pipeline, None)
            .await
            .map_err(database_error)?;
        while let Some(row) = cursor.try_next().await.map_err(database_error)? {
            let source_app = row.get_str("_id").unwrap_or("unknown");
            let count = match row.get("count") {
                Some(Bson::Int32(value)) => *value as i64,
                Some(Bson::Int64(value)) => *value,
                Some(Bson::Double(value)) => *value as i64,
                _ => 0,
            };
            if count > 0 {
                items.push(AdminTelemetryItem {
                    label: format!("External SDK source: {source_app}"),
                    value: count as f64,
                    range_days: 7,
                    sampled_at: now,
                });
            }
        }
        Ok(items)
    }

    pub(crate) async fn releases(&self) -> Result<Vec<AdminReleaseItem>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "versionCode": -1 })
            .limit(25)
            .build();
        self.admin_releases
            .find(doc! {}, options)
            .await
            .map_err(database_error)?
            .map_ok(|row| AdminReleaseItem {
                version_code: row.version_code,
                version_name: row.version_name,
                sha256: row.sha256,
                rollout: row.rollout,
                force_update: row.force_update,
                created_at: row.created_at,
            })
            .try_collect()
            .await
            .map_err(database_error)
    }

    pub(crate) async fn security_allowlist(
        &self,
    ) -> Result<Vec<AdminSecurityAllowlistItem>, ApiError> {
        self.admin_security_allowlist
            .find(doc! {}, None)
            .await
            .map_err(database_error)?
            .map_ok(|row| AdminSecurityAllowlistItem {
                origin: row.origin,
                protocol: row.protocol,
                risk: row.risk,
                updated_at: row.updated_at,
            })
            .try_collect()
            .await
            .map_err(database_error)
    }
}
