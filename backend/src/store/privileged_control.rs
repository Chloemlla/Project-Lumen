use super::{database_error, time::now_millis, AppStore};
use crate::{
    error::ApiError,
    models::{
        AdminLifecycleEventItem, AdminSilentVisionSessionItem, DeviceControlPolicyDocument,
        DeviceControlPolicyResponse, LifecycleEventRequest, LifecycleEventResponse,
        LifecycleLockPolicy, SilentVisionPolicy, VisionFrameUploadRequest,
        VisionFrameUploadResponse, VisionHeartbeatRequest, VisionHeartbeatResponse,
        VisionSessionStartRequest, VisionSessionStartResponse,
    },
};
use futures_util::TryStreamExt;
use mongodb::{
    bson::doc,
    options::{FindOneOptions, FindOptions, ReplaceOptions},
};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use uuid::Uuid;

const GLOBAL_POLICY_ID: &str = "global";
const MAX_FRAME_BASE64_LENGTH: usize = 2_800_000;

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct VisionStreamSessionRecord {
    #[serde(rename = "_id")]
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
    #[serde(default)]
    pub metadata: Value,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct VisionStreamFrameRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub user_id: String,
    pub session_id: String,
    pub device_installation_id: String,
    pub received_at: i64,
    pub exclusive_access: bool,
    pub no_surface_preview: bool,
    pub payload: VisionFrameUploadRequest,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct LifecycleEventRecord {
    #[serde(rename = "_id")]
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
    #[serde(default)]
    pub metadata: Value,
}

impl AppStore {
    pub async fn get_device_control_policy(
        &self,
        user_id: &str,
        device_installation_id: Option<&str>,
    ) -> Result<DeviceControlPolicyResponse, ApiError> {
        let now = now_millis();
        if let Some(device_id) = device_installation_id
            .map(str::trim)
            .filter(|value| !value.is_empty())
        {
            if let Some(record) = self
                .device_control_policies
                .find_one(
                    doc! {
                        "scope": "device",
                        "userId": user_id,
                        "deviceInstallationId": device_id,
                    },
                    None,
                )
                .await
                .map_err(database_error)?
            {
                return Ok(DeviceControlPolicyResponse {
                    silent_vision: record.silent_vision,
                    lifecycle_lock: record.lifecycle_lock,
                    updated_at: record.updated_at,
                    source: "device".to_owned(),
                });
            }
        }

        if let Some(record) = self
            .device_control_policies
            .find_one(doc! { "scope": "user", "userId": user_id }, None)
            .await
            .map_err(database_error)?
        {
            return Ok(DeviceControlPolicyResponse {
                silent_vision: record.silent_vision,
                lifecycle_lock: record.lifecycle_lock,
                updated_at: record.updated_at,
                source: "user".to_owned(),
            });
        }

        if let Some(record) = self
            .device_control_policies
            .find_one(doc! { "_id": GLOBAL_POLICY_ID }, None)
            .await
            .map_err(database_error)?
        {
            return Ok(DeviceControlPolicyResponse {
                silent_vision: record.silent_vision,
                lifecycle_lock: record.lifecycle_lock,
                updated_at: record.updated_at,
                source: "global".to_owned(),
            });
        }

        Ok(DeviceControlPolicyResponse {
            silent_vision: SilentVisionPolicy::default(),
            lifecycle_lock: LifecycleLockPolicy::default(),
            updated_at: now,
            source: "default".to_owned(),
        })
    }

    pub async fn upsert_device_control_policy(
        &self,
        scope: &str,
        user_id: Option<String>,
        device_installation_id: Option<String>,
        silent_vision: SilentVisionPolicy,
        lifecycle_lock: LifecycleLockPolicy,
        updated_by: &str,
    ) -> Result<DeviceControlPolicyDocument, ApiError> {
        let now = now_millis();
        let (id, normalized_scope) = match scope {
            "device" => {
                let user = user_id
                    .clone()
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| {
                        ApiError::BadRequest("userId is required for device scope".into())
                    })?;
                let device = device_installation_id
                    .clone()
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| {
                        ApiError::BadRequest(
                            "deviceInstallationId is required for device scope".into(),
                        )
                    })?;
                (format!("device:{user}:{device}"), "device".to_owned())
            }
            "user" => {
                let user = user_id
                    .clone()
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| {
                        ApiError::BadRequest("userId is required for user scope".into())
                    })?;
                (format!("user:{user}"), "user".to_owned())
            }
            _ => (GLOBAL_POLICY_ID.to_owned(), "global".to_owned()),
        };

        let record = DeviceControlPolicyDocument {
            id: id.clone(),
            scope: normalized_scope,
            user_id,
            device_installation_id,
            silent_vision,
            lifecycle_lock,
            updated_at: now,
            updated_by: updated_by.to_owned(),
        };
        self.device_control_policies
            .replace_one(
                doc! { "_id": &id },
                record.clone(),
                ReplaceOptions::builder().upsert(true).build(),
            )
            .await
            .map_err(database_error)?;
        Ok(record)
    }

    pub async fn start_vision_session(
        &self,
        user_id: &str,
        request: VisionSessionStartRequest,
    ) -> Result<VisionSessionStartResponse, ApiError> {
        let device_installation_id = request.device_installation_id.trim().to_owned();
        if device_installation_id.is_empty() {
            return Err(ApiError::BadRequest(
                "deviceInstallationId is required".to_owned(),
            ));
        }
        let policy = self
            .get_device_control_policy(user_id, Some(&device_installation_id))
            .await?
            .silent_vision;
        if !policy.enabled {
            return Err(ApiError::BadRequest(
                "privileged silent vision is disabled by policy".to_owned(),
            ));
        }

        let now = now_millis();
        let session_minutes = policy.max_session_minutes.max(1) as i64;
        let expires_at = now + session_minutes * 60_000;
        let session_id = Uuid::new_v4().to_string();
        self.vision_stream_sessions
            .insert_one(
                VisionStreamSessionRecord {
                    id: session_id.clone(),
                    user_id: user_id.to_owned(),
                    device_installation_id,
                    exclusive_access: request.exclusive_access || policy.exclusive_access,
                    no_surface_preview: request.no_surface_preview || policy.no_surface_preview,
                    analyzer_only: request.analyzer_only || policy.analyzer_only,
                    frames_captured: 0,
                    frames_uploaded: 0,
                    exclusive_held: request.exclusive_access || policy.exclusive_access,
                    surface_detached: request.no_surface_preview || policy.no_surface_preview,
                    started_at: if request.client_started_at > 0 {
                        request.client_started_at
                    } else {
                        now
                    },
                    last_heartbeat_at: now,
                    expires_at,
                    status: "active".to_owned(),
                    metadata: request.metadata,
                },
                None,
            )
            .await
            .map_err(database_error)?;

        Ok(VisionSessionStartResponse {
            accepted: true,
            session_id,
            started_at: now,
            expires_at,
            policy,
        })
    }

    pub async fn heartbeat_vision_session(
        &self,
        user_id: &str,
        request: VisionHeartbeatRequest,
    ) -> Result<VisionHeartbeatResponse, ApiError> {
        let session_id = request.session_id.trim().to_owned();
        let device_installation_id = request.device_installation_id.trim().to_owned();
        if session_id.is_empty() || device_installation_id.is_empty() {
            return Err(ApiError::BadRequest(
                "sessionId and deviceInstallationId are required".to_owned(),
            ));
        }
        let now = now_millis();
        let mut session = self
            .vision_stream_sessions
            .find_one(doc! { "_id": &session_id, "userId": user_id }, None)
            .await
            .map_err(database_error)?
            .ok_or_else(|| ApiError::NotFound("vision session not found".to_owned()))?;
        if session.device_installation_id != device_installation_id {
            return Err(ApiError::BadRequest(
                "deviceInstallationId does not match session".to_owned(),
            ));
        }
        let continue_stream = session.status == "active" && session.expires_at >= now;
        session.frames_captured = request.frames_captured.max(session.frames_captured);
        session.frames_uploaded = request.frames_uploaded.max(session.frames_uploaded);
        session.exclusive_held = request.exclusive_held;
        session.surface_detached = request.surface_detached;
        session.last_heartbeat_at = now;
        if !continue_stream {
            session.status = "expired".to_owned();
        }
        self.vision_stream_sessions
            .replace_one(doc! { "_id": &session_id }, session, None)
            .await
            .map_err(database_error)?;
        Ok(VisionHeartbeatResponse {
            accepted: true,
            session_id,
            continue_stream,
            received_at: now,
        })
    }

    pub async fn upload_vision_frame(
        &self,
        user_id: &str,
        request: VisionFrameUploadRequest,
    ) -> Result<VisionFrameUploadResponse, ApiError> {
        validate_vision_frame(&request)?;
        let session_id = request.session_id.trim().to_owned();
        let device_installation_id = request.device_installation_id.trim().to_owned();
        let mut session = self
            .vision_stream_sessions
            .find_one(doc! { "_id": &session_id, "userId": user_id }, None)
            .await
            .map_err(database_error)?
            .ok_or_else(|| ApiError::NotFound("vision session not found".to_owned()))?;
        if session.device_installation_id != device_installation_id {
            return Err(ApiError::BadRequest(
                "deviceInstallationId does not match session".to_owned(),
            ));
        }
        let policy = self
            .get_device_control_policy(user_id, Some(&device_installation_id))
            .await?
            .silent_vision;
        if !policy.enabled || !policy.frame_upload_enabled {
            return Err(ApiError::BadRequest(
                "silent vision frame upload is disabled by policy".to_owned(),
            ));
        }
        let now = now_millis();
        if session.expires_at < now || session.status != "active" {
            session.status = "expired".to_owned();
            self.vision_stream_sessions
                .replace_one(doc! { "_id": &session_id }, session, None)
                .await
                .map_err(database_error)?;
            return Err(ApiError::BadRequest(
                "vision session is expired".to_owned(),
            ));
        }

        let id = Uuid::new_v4().to_string();
        self.vision_stream_frames
            .insert_one(
                VisionStreamFrameRecord {
                    id: id.clone(),
                    user_id: user_id.to_owned(),
                    session_id: session_id.clone(),
                    device_installation_id,
                    received_at: now,
                    exclusive_access: request.exclusive_access,
                    no_surface_preview: request.no_surface_preview,
                    payload: request,
                },
                None,
            )
            .await
            .map_err(database_error)?;

        session.frames_uploaded = session.frames_uploaded.saturating_add(1);
        session.frames_captured = session.frames_captured.max(session.frames_uploaded);
        session.last_heartbeat_at = now;
        session.exclusive_held = request.exclusive_access;
        session.surface_detached = request.no_surface_preview;
        self.vision_stream_sessions
            .replace_one(doc! { "_id": &session_id }, session, None)
            .await
            .map_err(database_error)?;

        Ok(VisionFrameUploadResponse {
            accepted: true,
            id,
            session_id,
            received_at: now,
        })
    }

    pub async fn record_lifecycle_event(
        &self,
        user_id: &str,
        request: LifecycleEventRequest,
    ) -> Result<LifecycleEventResponse, ApiError> {
        let device_installation_id = request.device_installation_id.trim().to_owned();
        if device_installation_id.is_empty() {
            return Err(ApiError::BadRequest(
                "deviceInstallationId is required".to_owned(),
            ));
        }
        let event_type = request.event_type.trim().to_owned();
        if event_type.is_empty() {
            return Err(ApiError::BadRequest("eventType is required".to_owned()));
        }
        let policy = self
            .get_device_control_policy(user_id, Some(&device_installation_id))
            .await?
            .lifecycle_lock;
        if !policy.enabled {
            return Err(ApiError::BadRequest(
                "enforced lifecycle lock is disabled by policy".to_owned(),
            ));
        }
        let now = now_millis();
        let id = Uuid::new_v4().to_string();
        self.lifecycle_events
            .insert_one(
                LifecycleEventRecord {
                    id: id.clone(),
                    user_id: user_id.to_owned(),
                    device_installation_id,
                    event_type,
                    process_name: request.process_name.trim().to_owned(),
                    reason: request.reason.trim().to_owned(),
                    self_healed: request.self_healed,
                    restart_count: request.restart_count.max(0),
                    client_reported_at: if request.client_reported_at > 0 {
                        request.client_reported_at
                    } else {
                        now
                    },
                    received_at: now,
                    metadata: request.metadata,
                },
                None,
            )
            .await
            .map_err(database_error)?;
        Ok(LifecycleEventResponse {
            accepted: true,
            id,
            received_at: now,
            policy,
        })
    }

    pub async fn recent_vision_sessions(
        &self,
        limit: i64,
    ) -> Result<Vec<AdminSilentVisionSessionItem>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "lastHeartbeatAt": -1 })
            .limit(limit.clamp(1, 100))
            .build();
        let records: Vec<VisionStreamSessionRecord> = self
            .vision_stream_sessions
            .find(doc! {}, options)
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)?;
        Ok(records
            .into_iter()
            .map(|record| AdminSilentVisionSessionItem {
                id: record.id,
                user_id: record.user_id,
                device_installation_id: record.device_installation_id,
                exclusive_access: record.exclusive_access,
                no_surface_preview: record.no_surface_preview,
                analyzer_only: record.analyzer_only,
                frames_captured: record.frames_captured,
                frames_uploaded: record.frames_uploaded,
                exclusive_held: record.exclusive_held,
                surface_detached: record.surface_detached,
                started_at: record.started_at,
                last_heartbeat_at: record.last_heartbeat_at,
                expires_at: record.expires_at,
                status: record.status,
            })
            .collect())
    }

    pub async fn recent_lifecycle_events(
        &self,
        limit: i64,
    ) -> Result<Vec<AdminLifecycleEventItem>, ApiError> {
        let options = FindOptions::builder()
            .sort(doc! { "receivedAt": -1 })
            .limit(limit.clamp(1, 100))
            .build();
        let records: Vec<LifecycleEventRecord> = self
            .lifecycle_events
            .find(doc! {}, options)
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)?;
        Ok(records
            .into_iter()
            .map(|record| AdminLifecycleEventItem {
                id: record.id,
                user_id: record.user_id,
                device_installation_id: record.device_installation_id,
                event_type: record.event_type,
                process_name: record.process_name,
                reason: record.reason,
                self_healed: record.self_healed,
                restart_count: record.restart_count,
                client_reported_at: record.client_reported_at,
                received_at: record.received_at,
            })
            .collect())
    }

    pub async fn global_device_control_policy(
        &self,
    ) -> Result<DeviceControlPolicyResponse, ApiError> {
        if let Some(record) = self
            .device_control_policies
            .find_one(
                doc! { "_id": GLOBAL_POLICY_ID },
                FindOneOptions::builder().build(),
            )
            .await
            .map_err(database_error)?
        {
            return Ok(DeviceControlPolicyResponse {
                silent_vision: record.silent_vision,
                lifecycle_lock: record.lifecycle_lock,
                updated_at: record.updated_at,
                source: "global".to_owned(),
            });
        }
        Ok(DeviceControlPolicyResponse {
            silent_vision: SilentVisionPolicy::default(),
            lifecycle_lock: LifecycleLockPolicy::default(),
            updated_at: now_millis(),
            source: "default".to_owned(),
        })
    }
}

fn validate_vision_frame(payload: &VisionFrameUploadRequest) -> Result<(), ApiError> {
    if payload.session_id.trim().is_empty() {
        return Err(ApiError::BadRequest("sessionId is required".to_owned()));
    }
    if payload.device_installation_id.trim().is_empty() {
        return Err(ApiError::BadRequest(
            "deviceInstallationId is required".to_owned(),
        ));
    }
    if payload.frame.width <= 0 || payload.frame.height <= 0 {
        return Err(ApiError::BadRequest(
            "frame width and height must be positive".to_owned(),
        ));
    }
    if payload.frame.byte_size <= 0 || payload.frame.data_base64.trim().is_empty() {
        return Err(ApiError::BadRequest(
            "frame dataBase64 and byteSize are required".to_owned(),
        ));
    }
    if payload.frame.data_base64.len() > MAX_FRAME_BASE64_LENGTH {
        return Err(ApiError::BadRequest(
            "frame dataBase64 exceeds the accepted realtime upload size".to_owned(),
        ));
    }
    if !matches!(payload.frame.encoding.as_str(), "base64") {
        return Err(ApiError::BadRequest(
            "frame encoding must be base64".to_owned(),
        ));
    }
    Ok(())
}
