use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SilentVisionPolicy {
    pub enabled: bool,
    pub exclusive_access: bool,
    pub no_surface_preview: bool,
    pub analyzer_only: bool,
    pub requires_explicit_consent: bool,
    pub max_fps: i32,
    pub max_session_minutes: i32,
    pub frame_upload_enabled: bool,
    pub endpoint_prefix: String,
}

impl Default for SilentVisionPolicy {
    fn default() -> Self {
        Self {
            enabled: true,
            exclusive_access: true,
            no_surface_preview: true,
            analyzer_only: true,
            requires_explicit_consent: true,
            max_fps: 2,
            max_session_minutes: 120,
            frame_upload_enabled: true,
            endpoint_prefix: "/v1/device-control".to_owned(),
        }
    }
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LifecycleLockPolicy {
    pub enabled: bool,
    pub enforce_keepalive: bool,
    pub self_heal_on_kill: bool,
    pub intercept_user_stop: bool,
    pub anti_uninstall_intent: bool,
    pub restart_delay_ms: i64,
    pub max_restart_burst: i32,
    pub report_events: bool,
    pub endpoint_prefix: String,
}

impl Default for LifecycleLockPolicy {
    fn default() -> Self {
        Self {
            enabled: true,
            enforce_keepalive: true,
            self_heal_on_kill: true,
            intercept_user_stop: true,
            anti_uninstall_intent: true,
            restart_delay_ms: 0,
            max_restart_burst: 12,
            report_events: true,
            endpoint_prefix: "/v1/device-control".to_owned(),
        }
    }
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceControlPolicyDocument {
    #[serde(rename = "_id")]
    pub id: String,
    pub scope: String,
    pub user_id: Option<String>,
    pub device_installation_id: Option<String>,
    pub silent_vision: SilentVisionPolicy,
    pub lifecycle_lock: LifecycleLockPolicy,
    pub updated_at: i64,
    pub updated_by: String,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceControlPolicyResponse {
    pub silent_vision: SilentVisionPolicy,
    pub lifecycle_lock: LifecycleLockPolicy,
    pub updated_at: i64,
    pub source: String,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VisionSessionStartRequest {
    pub device_installation_id: String,
    pub exclusive_access: bool,
    pub no_surface_preview: bool,
    pub analyzer_only: bool,
    pub client_started_at: i64,
    #[serde(default)]
    pub metadata: Value,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct VisionSessionStartResponse {
    pub accepted: bool,
    pub session_id: String,
    pub started_at: i64,
    pub expires_at: i64,
    pub policy: SilentVisionPolicy,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VisionHeartbeatRequest {
    pub session_id: String,
    pub device_installation_id: String,
    pub frames_captured: i64,
    pub frames_uploaded: i64,
    pub exclusive_held: bool,
    pub surface_detached: bool,
    pub client_reported_at: i64,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct VisionHeartbeatResponse {
    pub accepted: bool,
    pub session_id: String,
    pub continue_stream: bool,
    pub received_at: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct VisionFrameUploadRequest {
    pub session_id: String,
    pub device_installation_id: String,
    pub captured_at: i64,
    pub exclusive_access: bool,
    pub no_surface_preview: bool,
    pub frame: crate::models::CameraFramePayload,
    #[serde(default)]
    pub faces: Vec<crate::models::FaceAnalysisFace>,
    pub processing: Option<crate::models::FaceAnalysisProcessingMetrics>,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct VisionFrameUploadResponse {
    pub accepted: bool,
    pub id: String,
    pub session_id: String,
    pub received_at: i64,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LifecycleEventRequest {
    pub device_installation_id: String,
    pub event_type: String,
    pub process_name: String,
    pub reason: String,
    pub self_healed: bool,
    pub restart_count: i32,
    pub client_reported_at: i64,
    #[serde(default)]
    pub metadata: Value,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LifecycleEventResponse {
    pub accepted: bool,
    pub id: String,
    pub received_at: i64,
    pub policy: LifecycleLockPolicy,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminSilentVisionSessionItem {
    pub id: String,
    pub user_id: String,
    pub device_installation_id: String,
    pub exclusive_access: bool,
    pub no_surface_preview: bool,
    pub analyzer_only: bool,
    pub frames_captured: i64,
    pub frames_uploaded: i64,
    pub exclusive_held: bool,
    pub surface_detached: bool,
    pub started_at: i64,
    pub last_heartbeat_at: i64,
    pub expires_at: i64,
    pub status: String,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminLifecycleEventItem {
    pub id: String,
    pub user_id: String,
    pub device_installation_id: String,
    pub event_type: String,
    pub process_name: String,
    pub reason: String,
    pub self_healed: bool,
    pub restart_count: i32,
    pub client_reported_at: i64,
    pub received_at: i64,
}

