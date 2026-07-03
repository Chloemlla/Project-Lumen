use super::{database_error, time::now_millis, AppStore};
use crate::{
    error::ApiError,
    models::{
        TelemetryDebugItem, TelemetryDebugLatestResponse, TelemetryUploadRequest,
        TelemetryUploadResponse,
    },
};
use futures_util::TryStreamExt;
use mongodb::{bson::doc, options::FindOptions};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct TelemetryUploadRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub user_id: String,
    pub device_installation_id: String,
    pub received_at: i64,
    pub payload: TelemetryUploadRequest,
}

impl AppStore {
    pub async fn record_telemetry_upload(
        &self,
        user_id: &str,
        mut request: TelemetryUploadRequest,
    ) -> Result<TelemetryUploadResponse, ApiError> {
        let device_installation_id = request.device_installation_id.trim().to_owned();
        if device_installation_id.is_empty() {
            return Err(ApiError::BadRequest(
                "deviceInstallationId is required for telemetry upload".to_owned(),
            ));
        }
        if device_installation_id.len() > MAX_DEVICE_INSTALLATION_ID_LENGTH {
            return Err(ApiError::BadRequest(
                "deviceInstallationId is too long for telemetry upload".to_owned(),
            ));
        }

        let id = Uuid::new_v4().to_string();
        let received_at = now_millis();
        self.enforce_telemetry_rate_limit(user_id, received_at)
            .await?;
        sanitize_telemetry_upload(&mut request);

        self.telemetry_uploads
            .insert_one(
                TelemetryUploadRecord {
                    id: id.clone(),
                    user_id: user_id.to_owned(),
                    device_installation_id,
                    received_at,
                    payload: request,
                },
                None,
            )
            .await
            .map_err(database_error)?;

        Ok(TelemetryUploadResponse {
            accepted: true,
            id,
            received_at,
        })
    }

    pub async fn latest_telemetry_debug_items(
        &self,
        user_id: &str,
        device_installation_id: Option<&str>,
    ) -> Result<TelemetryDebugLatestResponse, ApiError> {
        let mut filter = doc! { "userId": user_id };
        if let Some(device_id) = device_installation_id
            .map(str::trim)
            .filter(|value| !value.is_empty())
        {
            filter.insert("deviceInstallationId", device_id.to_owned());
        }
        let options = FindOptions::builder()
            .sort(doc! { "receivedAt": -1 })
            .limit(TELEMETRY_DEBUG_LATEST_LIMIT)
            .build();
        let items = self
            .telemetry_uploads
            .find(filter, options)
            .await
            .map_err(database_error)?
            .map_ok(|row| telemetry_debug_item(row))
            .try_collect()
            .await
            .map_err(database_error)?;

        Ok(TelemetryDebugLatestResponse { items })
    }

    async fn enforce_telemetry_rate_limit(
        &self,
        user_id: &str,
        received_at: i64,
    ) -> Result<(), ApiError> {
        let recent_window_start = received_at - TELEMETRY_RATE_LIMIT_WINDOW_MILLIS;
        let recent_count = self
            .telemetry_uploads
            .count_documents(
                doc! {
                    "userId": user_id,
                    "receivedAt": { "$gte": recent_window_start },
                },
                None,
            )
            .await
            .map_err(database_error)?;
        if recent_count >= TELEMETRY_UPLOADS_PER_HOUR_LIMIT {
            return Err(ApiError::TooManyRequests(
                "telemetry upload rate limit exceeded".to_owned(),
            ));
        }
        Ok(())
    }
}

fn sanitize_telemetry_upload(request: &mut TelemetryUploadRequest) {
    request.device_installation_id = request.device_installation_id.trim().to_owned();
    truncate_string(
        &mut request.device_installation_id,
        MAX_DEVICE_INSTALLATION_ID_LENGTH,
    );
    request.source_app = request.source_app.trim().to_owned();
    truncate_string(&mut request.source_app, MAX_SOURCE_APP_LENGTH);
    request
        .environment_context
        .truncate(MAX_ENVIRONMENT_CONTEXTS);

    truncate_string(
        &mut request.device_profile.device_fingerprint,
        DEVICE_FINGERPRINT_LENGTH,
    );
    if !is_device_fingerprint(&request.device_profile.device_fingerprint) {
        request.device_profile.device_fingerprint.clear();
    }
    truncate_string(
        &mut request.device_profile.manufacturer,
        MAX_SHORT_TEXT_LENGTH,
    );
    truncate_string(&mut request.device_profile.model, MAX_SHORT_TEXT_LENGTH);
    truncate_string(
        &mut request.device_profile.device_name,
        MAX_SHORT_TEXT_LENGTH,
    );
    truncate_string(
        &mut request.device_profile.android_release,
        MAX_SHORT_TEXT_LENGTH,
    );
    truncate_string(&mut request.device_profile.primary_abi, MAX_SHORT_TEXT_LENGTH);
    truncate_string(
        &mut request.device_profile.build_fingerprint,
        MAX_BUILD_FINGERPRINT_LENGTH,
    );
    truncate_string(
        &mut request.device_profile.front_camera_resolution,
        MAX_SHORT_TEXT_LENGTH,
    );
    truncate_string(
        &mut request.device_profile.app_version_name,
        MAX_SHORT_TEXT_LENGTH,
    );

    if let Some(daily_health) = &mut request.daily_health {
        truncate_string(&mut daily_health.stat_date, MAX_SHORT_TEXT_LENGTH);
        daily_health
            .distance_violations
            .truncate(MAX_DISTANCE_VIOLATIONS);
    }
    if let Some(pomodoro_productivity) = &mut request.pomodoro_productivity {
        truncate_string(&mut pomodoro_productivity.stat_date, MAX_SHORT_TEXT_LENGTH);
    }
    if let Some(developer_debug) = &mut request.developer_debug {
        developer_debug.crash_logs.truncate(MAX_CRASH_LOGS);
        for crash_log in &mut developer_debug.crash_logs {
            truncate_string(&mut crash_log.exception_type, MAX_CRASH_FIELD_LENGTH);
            truncate_string(&mut crash_log.root_cause, MAX_CRASH_FIELD_LENGTH);
            crash_log.stack_trace_lines.truncate(MAX_CRASH_STACK_LINES);
            for line in &mut crash_log.stack_trace_lines {
                truncate_string(line, MAX_CRASH_LINE_LENGTH);
            }
        }
        developer_debug.api_traces.truncate(MAX_API_TRACES);
        for trace in &mut developer_debug.api_traces {
            truncate_string(&mut trace.method, MAX_API_METHOD_LENGTH);
            truncate_string(&mut trace.path, MAX_API_PATH_LENGTH);
            truncate_string(&mut trace.error_type, MAX_CRASH_FIELD_LENGTH);
            truncate_string(&mut trace.error_message, MAX_CRASH_LINE_LENGTH);
            trace.duration_millis = trace.duration_millis.max(0);
        }
    }
    if let Some(device_diagnostics) = &mut request.device_diagnostics {
        truncate_string(
            &mut device_diagnostics.collection_source,
            MAX_SHORT_TEXT_LENGTH,
        );
        let original_len = device_diagnostics.user_apps.len();
        device_diagnostics.user_apps.retain(|app| {
            is_android_package_name(&app.package_name)
                && app.package_name.len() <= MAX_PACKAGE_FIELD_LENGTH
        });
        device_diagnostics
            .user_apps
            .truncate(MAX_DIAGNOSTIC_USER_APPS);
        device_diagnostics.user_apps_truncated =
            device_diagnostics.user_apps_truncated || original_len > MAX_DIAGNOSTIC_USER_APPS;
        device_diagnostics.user_app_count = device_diagnostics
            .user_app_count
            .max(device_diagnostics.user_apps.len() as i32)
            .max(0);
        for app in &mut device_diagnostics.user_apps {
            app.package_name = app.package_name.trim().to_owned();
            app.installer_package_name = app.installer_package_name.trim().to_owned();
            truncate_string(&mut app.package_name, MAX_PACKAGE_FIELD_LENGTH);
            truncate_string(&mut app.installer_package_name, MAX_PACKAGE_FIELD_LENGTH);
            if !app.installer_package_name.is_empty()
                && !is_android_package_name(&app.installer_package_name)
            {
                app.installer_package_name.clear();
            }
            app.version_code = app.version_code.map(|value| value.max(0));
            app.uid = app.uid.map(|value| value.max(0));
        }
    }
    if let Some(user_configuration) = &mut request.user_configuration {
        user_configuration
            .reminder_plans
            .truncate(MAX_CONFIGURATION_ITEMS);
        for plan in &mut user_configuration.reminder_plans {
            truncate_string(&mut plan.quiet_mode, MAX_SHORT_TEXT_LENGTH);
            plan.warn_interval_minutes = plan.warn_interval_minutes.max(0);
            plan.rest_duration_seconds = plan.rest_duration_seconds.max(0);
        }
        user_configuration
            .tip_templates
            .truncate(MAX_CONFIGURATION_ITEMS);
        for template in &mut user_configuration.tip_templates {
            truncate_string(&mut template.background_type, MAX_SHORT_TEXT_LENGTH);
            truncate_string(&mut template.countdown_style, MAX_SHORT_TEXT_LENGTH);
        }
        if let Some(goal) = &mut user_configuration.daily_goal {
            goal.rest_break_goal = goal.rest_break_goal.max(0);
            goal.max_continuous_work_minutes = goal.max_continuous_work_minutes.max(0);
            goal.pomodoro_goal = goal.pomodoro_goal.max(0);
            goal.weekly_active_days_goal = goal.weekly_active_days_goal.max(0);
        }
        if let Some(audio) = &mut user_configuration.audio_feedback {
            audio.pre_alert_volume_percent = audio.pre_alert_volume_percent.clamp(0, 100);
            audio.rest_start_volume_percent = audio.rest_start_volume_percent.clamp(0, 100);
            audio.rest_end_volume_percent = audio.rest_end_volume_percent.clamp(0, 100);
            audio.pomodoro_work_start_volume_percent =
                audio.pomodoro_work_start_volume_percent.clamp(0, 100);
            audio.pomodoro_work_end_volume_percent =
                audio.pomodoro_work_end_volume_percent.clamp(0, 100);
        }
    }
}

fn telemetry_debug_item(row: TelemetryUploadRecord) -> TelemetryDebugItem {
    let device_diagnostics = row.payload.device_diagnostics.as_ref();
    let developer_debug = row.payload.developer_debug.as_ref();
    TelemetryDebugItem {
        id: row.id,
        device_installation_id: row.device_installation_id,
        received_at: row.received_at,
        source_app: row.payload.source_app,
        recorded_at: row.payload.recorded_at,
        has_device_diagnostics: device_diagnostics.is_some(),
        diagnostic_user_app_count: device_diagnostics
            .map(|diagnostics| diagnostics.user_app_count)
            .unwrap_or_default(),
        diagnostic_user_apps_stored: device_diagnostics
            .map(|diagnostics| diagnostics.user_apps.len())
            .unwrap_or_default(),
        diagnostic_user_apps_truncated: device_diagnostics
            .map(|diagnostics| diagnostics.user_apps_truncated)
            .unwrap_or_default(),
        crash_log_count: developer_debug
            .map(|debug| debug.crash_logs.len())
            .unwrap_or_default(),
    }
}

fn truncate_string(value: &mut String, max_chars: usize) {
    if value.chars().count() > max_chars {
        *value = value.chars().take(max_chars).collect();
    }
}

fn is_android_package_name(value: &str) -> bool {
    let mut segments = value.trim().split('.');
    let first = segments.next().unwrap_or_default();
    if !is_package_segment(first, true) {
        return false;
    }
    let mut segment_count = 1;
    for segment in segments {
        segment_count += 1;
        if !is_package_segment(segment, false) {
            return false;
        }
    }
    segment_count >= 2
}

fn is_package_segment(value: &str, first_segment: bool) -> bool {
    let mut chars = value.chars();
    let first = chars.next().unwrap_or_default();
    if !(first.is_ascii_alphabetic() || (!first_segment && first == '_')) {
        return false;
    }
    chars.all(|character| character.is_ascii_alphanumeric() || character == '_')
}

fn is_device_fingerprint(value: &str) -> bool {
    value.len() == DEVICE_FINGERPRINT_LENGTH
        && value.chars().all(|character| character.is_ascii_hexdigit())
}

const MAX_DEVICE_INSTALLATION_ID_LENGTH: usize = 128;
const DEVICE_FINGERPRINT_LENGTH: usize = 64;
const MAX_SOURCE_APP_LENGTH: usize = 64;
const MAX_SHORT_TEXT_LENGTH: usize = 160;
const MAX_BUILD_FINGERPRINT_LENGTH: usize = 256;
const MAX_ENVIRONMENT_CONTEXTS: usize = 16;
const MAX_DISTANCE_VIOLATIONS: usize = 16;
const MAX_CRASH_LOGS: usize = 4;
const MAX_CRASH_STACK_LINES: usize = 64;
const MAX_CRASH_LINE_LENGTH: usize = 500;
const MAX_CRASH_FIELD_LENGTH: usize = 240;
const MAX_API_TRACES: usize = 12;
const MAX_API_METHOD_LENGTH: usize = 12;
const MAX_API_PATH_LENGTH: usize = 240;
const MAX_PACKAGE_FIELD_LENGTH: usize = 160;
const MAX_DIAGNOSTIC_USER_APPS: usize = 150;
const MAX_CONFIGURATION_ITEMS: usize = 24;
const TELEMETRY_RATE_LIMIT_WINDOW_MILLIS: i64 = 60 * 60 * 1_000;
const TELEMETRY_UPLOADS_PER_HOUR_LIMIT: u64 = 60;
const TELEMETRY_DEBUG_LATEST_LIMIT: i64 = 20;
