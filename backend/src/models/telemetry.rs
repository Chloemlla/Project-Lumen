use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TelemetryUploadRequest {
    pub device_installation_id: String,
    #[serde(default = "default_source_app")]
    pub source_app: String,
    pub recorded_at: i64,
    pub daily_health: Option<DailyEyeHealthTelemetry>,
    #[serde(default)]
    pub environment_context: Vec<EnvironmentContextTelemetry>,
    pub device_profile: DeviceProfileTelemetry,
    pub calibration_anchor: Option<CalibrationAnchorTelemetry>,
    pub ai_performance: Option<AiPerformanceTelemetry>,
    pub developer_debug: Option<DeveloperDebugTelemetry>,
    pub device_diagnostics: Option<DeviceDiagnosticsTelemetry>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DailyEyeHealthTelemetry {
    pub stat_date: String,
    pub total_screen_seconds: i64,
    pub rest_seconds: i64,
    pub continuous_over_twenty_count: i32,
    pub max_continuous_work_seconds: i64,
    pub distance_violation_count: i32,
    pub distance_close_seconds: i64,
    #[serde(default)]
    pub distance_violations: Vec<DistanceViolationTelemetry>,
    pub blink_metrics: BlinkMetricsTelemetry,
    pub rest_compliance: RestComplianceTelemetry,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DistanceViolationTelemetry {
    pub recorded_at: i64,
    pub distance_factor: f64,
    pub ratio_percent: i32,
    pub close_seconds: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BlinkMetricsTelemetry {
    pub average_blinks_per_minute: Option<f64>,
    pub eye_dry_warning_count: i32,
    pub severe_eye_dry_risk: bool,
    pub last_eye_open_probability_percent: Option<i32>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RestComplianceTelemetry {
    pub completed_break_count: i32,
    pub skipped_break_count: i32,
    pub compliance_rate_percent: i32,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct EnvironmentContextTelemetry {
    pub recorded_at: i64,
    pub lux_level: i32,
    pub pose_status: String,
    pub scenario_status: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceProfileTelemetry {
    pub manufacturer: String,
    pub model: String,
    pub android_release: String,
    pub android_sdk: i32,
    pub front_camera_resolution: String,
    pub app_version_name: String,
    pub app_version_code: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceDiagnosticsTelemetry {
    pub consent_active_at: i64,
    pub collected_at: i64,
    pub collection_source: String,
    pub shizuku_ready: bool,
    pub shizuku_server_version: i32,
    pub shizuku_server_uid: i32,
    pub user_app_count: i32,
    pub user_apps_truncated: bool,
    #[serde(default)]
    pub user_apps: Vec<InstalledAppTelemetry>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InstalledAppTelemetry {
    pub package_name: String,
    pub installer_package_name: String,
    pub version_code: Option<i64>,
    pub uid: Option<i32>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CalibrationAnchorTelemetry {
    pub standard_distance_cm: i32,
    pub base_face_width_percent: i32,
    pub base_eye_distance_px: f64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AiPerformanceTelemetry {
    pub average_face_detection_ms: i64,
    pub camera_latency_ms: i64,
    pub background_kill_count: i32,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DeveloperDebugTelemetry {
    pub sensor_disturbance: Option<SensorDisturbanceTelemetry>,
    #[serde(default)]
    pub crash_logs: Vec<CrashLogTelemetry>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SensorDisturbanceTelemetry {
    pub pitch_degrees: f64,
    pub roll_degrees: f64,
    pub yaw_degrees: f64,
    pub acceleration_magnitude: f64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CrashLogTelemetry {
    pub crashed_at: i64,
    pub exception_type: String,
    pub root_cause: String,
    #[serde(default)]
    pub stack_trace_lines: Vec<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TelemetryUploadResponse {
    pub accepted: bool,
    pub id: String,
    pub received_at: i64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TelemetryDebugLatestResponse {
    pub items: Vec<TelemetryDebugItem>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TelemetryDebugItem {
    pub id: String,
    pub device_installation_id: String,
    pub received_at: i64,
    pub source_app: String,
    pub recorded_at: i64,
    pub has_device_diagnostics: bool,
    pub diagnostic_user_app_count: i32,
    pub diagnostic_user_apps_stored: usize,
    pub diagnostic_user_apps_truncated: bool,
    pub crash_log_count: usize,
}

fn default_source_app() -> String {
    "project_lumen".to_owned()
}
