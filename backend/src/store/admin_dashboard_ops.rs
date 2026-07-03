use super::{
    database_error,
    documents::{AdminAccessAuditRecord, StoredSyncChange},
    telemetry::TelemetryUploadRecord,
    time::now_millis,
    AppStore,
};
use crate::{
    error::ApiError,
    models::{
        AdminApiMetric, AdminAudioMatrixItem, AdminCrashGroup, AdminReleaseAssetItem,
        AdminReleaseItem, AdminReleasePatchItem, AdminSecurityAllowlistItem, AdminSyncMetric,
        AdminTelemetryItem, AdminTemplateItem,
    },
};
use futures_util::TryStreamExt;
use mongodb::{
    bson::{doc, Bson},
    options::{FindOneOptions, FindOptions},
};
use std::collections::{HashMap, HashSet};

impl AppStore {
    pub(crate) async fn crash_groups(&self) -> Result<Vec<AdminCrashGroup>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "lastSeenAt": -1 })
            .limit(25)
            .build();
        let stored: Vec<AdminCrashGroup> = self
            .admin_crash_reports
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
            .map_err(database_error)?;
        if !stored.is_empty() {
            return Ok(stored);
        }
        self.derived_crash_groups().await
    }

    pub(crate) async fn api_metrics(&self) -> Result<Vec<AdminApiMetric>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "sampledAt": -1 })
            .limit(25)
            .build();
        let stored: Vec<AdminApiMetric> = self
            .admin_api_metrics
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
            .map_err(database_error)?;
        if !stored.is_empty() {
            return Ok(stored);
        }
        self.derived_api_metrics().await
    }

    pub(crate) async fn sync_metrics(&self) -> Result<Vec<AdminSyncMetric>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "sampledAt": -1 })
            .limit(25)
            .build();
        let stored: Vec<AdminSyncMetric> = self
            .admin_sync_metrics
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
            .map_err(database_error)?;
        if !stored.is_empty() {
            return Ok(stored);
        }
        self.derived_sync_metrics().await
    }

    async fn derived_crash_groups(&self) -> Result<Vec<AdminCrashGroup>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "receivedAt": -1 })
            .limit(500)
            .build();
        let rows: Vec<TelemetryUploadRecord> = self
            .telemetry_uploads
            .find(
                doc! { "payload.developerDebug.crashLogs.0": { "$exists": true } },
                options,
            )
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)?;

        let mut groups: HashMap<String, CrashAccumulator> = HashMap::new();
        for row in rows {
            let Some(debug) = row.payload.developer_debug else {
                continue;
            };
            let version_code = row.payload.device_profile.app_version_code;
            for crash in debug.crash_logs {
                let group_key = crash_group_key(&crash.exception_type, &crash.root_cause);
                let accumulator =
                    groups
                        .entry(group_key.clone())
                        .or_insert_with(|| CrashAccumulator {
                            group_key,
                            version_code,
                            count: 0,
                            affected_users: HashSet::new(),
                            clean_stack: crash.stack_trace_lines.clone(),
                            last_seen_at: crash.crashed_at.max(row.received_at),
                        });
                accumulator.count += 1;
                accumulator.affected_users.insert(row.user_id.clone());
                accumulator.last_seen_at = accumulator
                    .last_seen_at
                    .max(crash.crashed_at.max(row.received_at));
                if accumulator.clean_stack.is_empty() {
                    accumulator.clean_stack = crash.stack_trace_lines;
                }
            }
        }

        let mut items = groups
            .into_values()
            .map(|group| AdminCrashGroup {
                group_key: group.group_key,
                version_code: group.version_code,
                count: group.count,
                affected_users: group.affected_users.len() as i64,
                risk: crash_risk(group.count, group.affected_users.len() as i64).to_owned(),
                clean_stack: group.clean_stack.into_iter().take(20).collect(),
            })
            .collect::<Vec<_>>();
        items.sort_by(|left, right| {
            right
                .count
                .cmp(&left.count)
                .then(right.affected_users.cmp(&left.affected_users))
        });
        items.truncate(25);
        Ok(items)
    }

    async fn derived_api_metrics(&self) -> Result<Vec<AdminApiMetric>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "receivedAt": -1 })
            .limit(500)
            .build();
        let telemetry_rows: Vec<TelemetryUploadRecord> = self
            .telemetry_uploads
            .find(
                doc! { "payload.developerDebug.apiTraces.0": { "$exists": true } },
                options,
            )
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)?;
        let mut by_path: HashMap<String, ApiMetricAccumulator> = HashMap::new();
        let now = now_millis();
        let mut oldest = now;
        for row in telemetry_rows {
            oldest = oldest.min(row.received_at);
            let Some(debug) = row.payload.developer_debug else {
                continue;
            };
            for trace in debug.api_traces {
                let path = trace.path.trim().to_owned();
                if path.is_empty() {
                    continue;
                }
                let accumulator = by_path.entry(path).or_default();
                accumulator.durations.push(trace.duration_millis.max(0));
                match trace.status_code.unwrap_or_default() {
                    200..=299 => accumulator.status_2xx += 1,
                    400..=499 => accumulator.status_4xx += 1,
                    500..=599 => accumulator.status_5xx += 1,
                    _ => {}
                }
            }
        }

        if by_path.is_empty() {
            return self.derived_api_metrics_from_audit().await;
        }

        let window_seconds = ((now - oldest).max(1_000) as f64 / 1_000.0).max(1.0);
        let mut items = by_path
            .into_iter()
            .map(|(endpoint, mut accumulator)| {
                accumulator.durations.sort_unstable();
                let count = accumulator.durations.len() as f64;
                AdminApiMetric {
                    endpoint,
                    qps: count / window_seconds,
                    p95_ms: percentile_95(&accumulator.durations),
                    status_2xx: accumulator.status_2xx,
                    status_4xx: accumulator.status_4xx,
                    status_5xx: accumulator.status_5xx,
                    sampled_at: now,
                }
            })
            .collect::<Vec<_>>();
        items.sort_by(|left, right| right.p95_ms.cmp(&left.p95_ms));
        items.truncate(25);
        Ok(items)
    }

    async fn derived_api_metrics_from_audit(&self) -> Result<Vec<AdminApiMetric>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "at": -1 })
            .limit(500)
            .build();
        let rows: Vec<AdminAccessAuditRecord> = self
            .admin_access_audit
            .find(doc! {}, options)
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)?;
        let now = now_millis();
        let oldest = rows.iter().map(|row| row.at).min().unwrap_or(now);
        let window_seconds = ((now - oldest).max(1_000) as f64 / 1_000.0).max(1.0);
        let mut by_endpoint: HashMap<String, ApiMetricAccumulator> = HashMap::new();
        for row in rows {
            let accumulator = by_endpoint.entry(row.endpoint).or_default();
            match row.status {
                200..=299 => accumulator.status_2xx += 1,
                400..=499 => accumulator.status_4xx += 1,
                500..=599 => accumulator.status_5xx += 1,
                _ => {}
            }
        }
        let mut items = by_endpoint
            .into_iter()
            .map(|(endpoint, accumulator)| {
                let count =
                    accumulator.status_2xx + accumulator.status_4xx + accumulator.status_5xx;
                AdminApiMetric {
                    endpoint,
                    qps: count as f64 / window_seconds,
                    p95_ms: 0,
                    status_2xx: accumulator.status_2xx,
                    status_4xx: accumulator.status_4xx,
                    status_5xx: accumulator.status_5xx,
                    sampled_at: now,
                }
            })
            .collect::<Vec<_>>();
        items.sort_by(|left, right| {
            (right.status_5xx + right.status_4xx).cmp(&(left.status_5xx + left.status_4xx))
        });
        items.truncate(25);
        Ok(items)
    }

    async fn derived_sync_metrics(&self) -> Result<Vec<AdminSyncMetric>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "cursor": -1 })
            .limit(500)
            .build();
        let rows: Vec<StoredSyncChange> = self
            .sync_changes
            .find(doc! {}, options)
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)?;
        if rows.is_empty() {
            return Ok(Vec::new());
        }
        let mut payload_sizes = Vec::with_capacity(rows.len());
        for row in rows {
            payload_sizes.push(
                serde_json::to_vec(&row.change.payload)
                    .map(|bytes| bytes.len() as i64)
                    .unwrap_or_default(),
            );
        }
        payload_sizes.sort_unstable();
        let total_bytes: i64 = payload_sizes.iter().sum();
        let average_payload_kb = bytes_to_kb(total_bytes / payload_sizes.len().max(1) as i64);
        let largest_payload_kb = bytes_to_kb(*payload_sizes.last().unwrap_or(&0));
        Ok(vec![AdminSyncMetric {
            endpoint: "/api/v1/sync/push".to_owned(),
            average_payload_kb,
            largest_payload_kb,
            p95_ms: 0,
            rejected_payloads: 0,
            sampled_at: now_millis(),
        }])
    }

    pub(crate) async fn template_catalog(&self) -> Result<Vec<AdminTemplateItem>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "updatedAt": -1 })
            .build();
        self.admin_templates
            .find(doc! { "updatedAt": { "$gt": 0 } }, options)
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
                updated_at: row.updated_at,
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

    pub(crate) async fn audio_matrix(&self) -> Result<Vec<AdminAudioMatrixItem>, ApiError> {
        let options = FindOneOptions::builder()
            .sort(doc! { "receivedAt": -1 })
            .build();
        let latest = self
            .telemetry_uploads
            .find_one(
                doc! { "payload.userConfiguration.audioFeedback": { "$exists": true } },
                options,
            )
            .await
            .map_err(database_error)?;
        let Some(row) = latest else {
            return Ok(Vec::new());
        };
        let Some(config) = row.payload.user_configuration else {
            return Ok(Vec::new());
        };
        let Some(audio) = config.audio_feedback else {
            return Ok(Vec::new());
        };
        let sampled_at = row.received_at;
        Ok(vec![
            AdminAudioMatrixItem {
                label: "Pre alert".to_owned(),
                enabled: audio.sound_enabled && audio.pre_alert_sound_enabled,
                volume_percent: audio.pre_alert_volume_percent,
                meta: "pre-alert reminder tone".to_owned(),
                sampled_at,
            },
            AdminAudioMatrixItem {
                label: "Rest start".to_owned(),
                enabled: audio.sound_enabled && audio.rest_start_sound_enabled,
                volume_percent: audio.rest_start_volume_percent,
                meta: "rest transition tone".to_owned(),
                sampled_at,
            },
            AdminAudioMatrixItem {
                label: "Rest end".to_owned(),
                enabled: audio.sound_enabled,
                volume_percent: audio.rest_end_volume_percent,
                meta: "rest completion tone".to_owned(),
                sampled_at,
            },
            AdminAudioMatrixItem {
                label: "Pomodoro start".to_owned(),
                enabled: audio.sound_enabled && audio.pomodoro_work_start_sound_enabled,
                volume_percent: audio.pomodoro_work_start_volume_percent,
                meta: "focus start tone".to_owned(),
                sampled_at,
            },
            AdminAudioMatrixItem {
                label: "Pomodoro end".to_owned(),
                enabled: audio.sound_enabled && audio.pomodoro_work_end_sound_enabled,
                volume_percent: audio.pomodoro_work_end_volume_percent,
                meta: "focus completion tone".to_owned(),
                sampled_at,
            },
            AdminAudioMatrixItem {
                label: "Vibration".to_owned(),
                enabled: audio.vibration_enabled,
                volume_percent: 0,
                meta: "system haptic feedback".to_owned(),
                sampled_at,
            },
        ])
    }

    pub(crate) async fn releases(&self) -> Result<Vec<AdminReleaseItem>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "versionCode": -1 })
            .limit(25)
            .build();
        self.admin_releases
            .find(doc! { "createdAt": { "$gt": 0 } }, options)
            .await
            .map_err(database_error)?
            .map_ok(|row| AdminReleaseItem {
                version_code: row.version_code,
                version_name: row.version_name,
                channel: row.channel,
                release_url: row.release_url,
                sha256: row.sha256,
                assets: row
                    .assets
                    .into_iter()
                    .map(|asset| AdminReleaseAssetItem {
                        abi: asset.abi,
                        name: asset.name,
                        url: asset.url,
                        sha256: asset.sha256,
                        size_bytes: asset.size_bytes,
                        content_type: asset.content_type,
                    })
                    .collect(),
                patches: row
                    .patches
                    .into_iter()
                    .map(|patch| AdminReleasePatchItem {
                        from_version_code: patch.from_version_code,
                        from_sha256: patch.from_sha256,
                        to_sha256: patch.to_sha256,
                        patch_url: patch.patch_url,
                        patch_sha256: patch.patch_sha256,
                        algorithm: patch.algorithm,
                        size_bytes: patch.size_bytes,
                    })
                    .collect(),
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
            .find(doc! { "updatedAt": { "$gt": 0 } }, None)
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

#[derive(Default)]
struct ApiMetricAccumulator {
    durations: Vec<i64>,
    status_2xx: i64,
    status_4xx: i64,
    status_5xx: i64,
}

struct CrashAccumulator {
    group_key: String,
    version_code: i64,
    count: i64,
    affected_users: HashSet<String>,
    clean_stack: Vec<String>,
    last_seen_at: i64,
}

fn crash_group_key(exception_type: &str, root_cause: &str) -> String {
    let exception = exception_type.trim();
    let cause = root_cause.trim();
    match (exception.is_empty(), cause.is_empty()) {
        (true, true) => "Unknown crash".to_owned(),
        (true, false) => cause.to_owned(),
        (false, true) => exception.to_owned(),
        (false, false) => format!("{exception} @ {cause}"),
    }
}

fn crash_risk(count: i64, affected_users: i64) -> &'static str {
    if count >= 10 || affected_users >= 5 {
        "risk"
    } else if count >= 3 || affected_users >= 2 {
        "watch"
    } else {
        "ok"
    }
}

fn percentile_95(sorted_values: &[i64]) -> i64 {
    if sorted_values.is_empty() {
        return 0;
    }
    let index = ((sorted_values.len() as f64 - 1.0) * 0.95).round() as usize;
    sorted_values[index.min(sorted_values.len() - 1)]
}

fn bytes_to_kb(bytes: i64) -> i64 {
    ((bytes.max(0) as f64) / 1024.0).round() as i64
}
