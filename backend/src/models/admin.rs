use super::privileged_control::{AdminLifecycleEventItem, AdminSilentVisionSessionItem};
use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminLoginRequest {
    pub username: String,
    pub password: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminRefreshRequest {
    pub refresh_token: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminSessionResponse {
    pub access_token: String,
    pub refresh_token: String,
    pub token_type: String,
    pub expires_at: i64,
    pub refresh_expires_at: i64,
    pub operator: AdminOperatorDto,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminOperatorDto {
    pub id: String,
    pub username: String,
    pub role: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminActionRequest {
    pub action: String,
    pub payload: Value,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminActionResponse {
    pub accepted: bool,
    pub action: String,
    pub recorded_at: i64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminDashboardResponse {
    pub generated_at: i64,
    pub users: AdminUsersSection,
    pub observability: AdminObservabilitySection,
    pub content: AdminContentSection,
    pub release: AdminReleaseSection,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminUsersSection {
    pub profiles: Vec<AdminUserProfile>,
    pub devices: Vec<AdminDeviceAsset>,
    pub access_audit: Vec<AdminAccessAuditEntry>,
    pub entitlements: Vec<AdminEntitlementItem>,
    pub purchase_audit: Vec<AdminPurchaseAuditEntry>,
    pub backups: Vec<AdminBackupSnapshot>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminUserProfile {
    pub id: String,
    pub email: String,
    pub registered_at: i64,
    pub last_sync_at: i64,
    pub plan_tier: String,
    pub feature_flags: Vec<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminDeviceAsset {
    pub user_id: String,
    pub device_installation_id: String,
    pub device_fingerprint: String,
    pub model: String,
    pub version_code: i64,
    pub last_seen_at: i64,
    pub local_security_config: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminAccessAuditEntry {
    pub at: i64,
    pub user_id: String,
    pub endpoint: String,
    pub ip: String,
    pub geo: String,
    pub status: u16,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminEntitlementItem {
    pub user_id: String,
    pub product_id: String,
    pub tier: String,
    pub status: String,
    pub expires_at: i64,
    pub last_verified_at: i64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminPurchaseAuditEntry {
    pub at: i64,
    pub user_id: String,
    pub product_id: String,
    pub purchase_token: String,
    pub status: String,
    pub action: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminBackupSnapshot {
    pub id: String,
    pub user_id: String,
    pub uploaded_at: i64,
    pub schema_version: i32,
    pub summary: AdminBackupSummary,
}

#[derive(Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminBackupSummary {
    pub templates: usize,
    pub eye_stat_days: usize,
    pub pomodoro_days: usize,
    pub reminder_plans: usize,
    pub entitlements: usize,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminObservabilitySection {
    pub crash_groups: Vec<AdminCrashGroup>,
    pub clean_stack: Vec<String>,
    pub version_impacts: Vec<AdminVersionImpactItem>,
    pub api_metrics: Vec<AdminApiMetric>,
    pub sync_metrics: Vec<AdminSyncMetric>,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminCrashGroup {
    pub group_key: String,
    pub version_code: i64,
    pub count: i64,
    pub affected_users: i64,
    pub risk: String,
    pub clean_stack: Vec<String>,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminApiMetric {
    pub endpoint: String,
    pub qps: f64,
    pub p95_ms: i64,
    pub status_2xx: i64,
    pub status_4xx: i64,
    pub status_5xx: i64,
    pub sampled_at: i64,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminSyncMetric {
    pub endpoint: String,
    pub average_payload_kb: i64,
    pub largest_payload_kb: i64,
    pub p95_ms: i64,
    pub rejected_payloads: i64,
    pub sampled_at: i64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminVersionImpactItem {
    pub version_code: i64,
    pub manufacturer: String,
    pub crash_count: i64,
    pub affected_users: i64,
    pub trend: String,
    pub risk: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminContentSection {
    pub templates: Vec<AdminTemplateItem>,
    pub audio_matrix: Vec<AdminAudioMatrixItem>,
    pub i18n_jobs: Vec<AdminI18nJobItem>,
    pub telemetry: Vec<AdminTelemetryItem>,
    pub silent_vision_sessions: Vec<AdminSilentVisionSessionItem>,
    pub lifecycle_events: Vec<AdminLifecycleEventItem>,
    pub device_control_policy: crate::models::DeviceControlPolicyResponse,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminTemplateItem {
    pub id: String,
    pub name: String,
    pub tier: String,
    pub countdown_style: String,
    pub color: String,
    pub locales: Vec<String>,
    pub layout_json: Value,
    pub updated_at: i64,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminTelemetryItem {
    pub label: String,
    pub value: f64,
    pub range_days: i64,
    pub sampled_at: i64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminAudioMatrixItem {
    pub label: String,
    pub enabled: bool,
    pub volume_percent: i32,
    pub meta: String,
    pub sampled_at: i64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminI18nJobItem {
    pub locale: String,
    pub template_count: usize,
    pub premium_count: usize,
    pub status: String,
    pub updated_at: i64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminReleaseSection {
    pub releases: Vec<AdminReleaseItem>,
    pub rollout_plan: Vec<AdminRolloutPlanItem>,
    pub routes: Vec<AdminRouteStatusItem>,
    pub allowlist: Vec<AdminSecurityAllowlistItem>,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminReleaseItem {
    pub version_code: i64,
    pub version_name: String,
    pub channel: String,
    pub release_url: String,
    pub sha256: String,
    pub assets: Vec<AdminReleaseAssetItem>,
    pub patches: Vec<AdminReleasePatchItem>,
    pub rollout: String,
    pub force_update: bool,
    pub created_at: i64,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminReleaseAssetItem {
    pub abi: String,
    pub name: String,
    pub url: String,
    pub sha256: String,
    pub size_bytes: i64,
    pub content_type: String,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminReleasePatchItem {
    pub from_version_code: i64,
    pub from_sha256: String,
    pub to_sha256: String,
    pub patch_url: String,
    pub patch_sha256: String,
    pub algorithm: String,
    pub size_bytes: i64,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminRouteStatusItem {
    pub module: String,
    pub path: String,
    pub state: String,
    pub p95_ms: i64,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminSecurityAllowlistItem {
    pub origin: String,
    pub protocol: String,
    pub risk: String,
    pub updated_at: i64,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminRolloutPlanItem {
    pub title: String,
    pub detail: String,
    pub status: String,
}
