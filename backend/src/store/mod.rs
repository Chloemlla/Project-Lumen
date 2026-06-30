mod admin_actions;
mod admin_audit;
mod admin_auth;
mod admin_dashboard;
mod admin_dashboard_ops;
mod auth;
mod backups;
mod documents;
mod entitlements;
mod face_analysis;
mod sync;
mod telemetry;
mod time;

use crate::{config::Config, error::ApiError};
use documents::{
    AdminAccessAuditRecord, AdminActionAuditRecord, AdminApiMetricRecord, AdminCrashReportRecord,
    AdminReleaseRecord, AdminSecurityAllowlistRecord, AdminSessionRecord, AdminSyncMetricRecord,
    AdminTelemetryRecord, AdminTemplateRecord, BackupRecord, CounterRecord, EntitlementRecord,
    PendingLogin, SessionRecord, StoredSyncChange,
};
use face_analysis::FaceAnalysisFrameRecord;
use mongodb::{
    bson::doc,
    options::{ClientOptions, IndexOptions, UpdateOptions},
    Client, Collection, IndexModel,
};
use serde_json::json;
use telemetry::TelemetryUploadRecord;

pub use documents::UserRecord;

pub struct AppStore {
    pub(crate) users: Collection<UserRecord>,
    pub(crate) login_requests: Collection<PendingLogin>,
    pub(crate) sessions: Collection<SessionRecord>,
    pub(crate) entitlements: Collection<EntitlementRecord>,
    pub(crate) sync_changes: Collection<StoredSyncChange>,
    pub(crate) telemetry_uploads: Collection<TelemetryUploadRecord>,
    pub(crate) face_analysis_frames: Collection<FaceAnalysisFrameRecord>,
    pub(crate) backups: Collection<BackupRecord>,
    pub(crate) counters: Collection<CounterRecord>,
    pub(crate) admin_sessions: Collection<AdminSessionRecord>,
    pub(crate) admin_actions: Collection<AdminActionAuditRecord>,
    pub(crate) admin_access_audit: Collection<AdminAccessAuditRecord>,
    pub(crate) admin_crash_reports: Collection<AdminCrashReportRecord>,
    pub(crate) admin_api_metrics: Collection<AdminApiMetricRecord>,
    pub(crate) admin_sync_metrics: Collection<AdminSyncMetricRecord>,
    pub(crate) admin_templates: Collection<AdminTemplateRecord>,
    pub(crate) admin_telemetry: Collection<AdminTelemetryRecord>,
    pub(crate) admin_releases: Collection<AdminReleaseRecord>,
    pub(crate) admin_security_allowlist: Collection<AdminSecurityAllowlistRecord>,
}

impl AppStore {
    pub async fn connect(config: &Config) -> Result<Self, ApiError> {
        let client_options = ClientOptions::parse(&config.mongodb_uri)
            .await
            .map_err(database_error)?;
        let client = Client::with_options(client_options).map_err(database_error)?;
        let database = client.database(&config.mongodb_database);
        let store = Self {
            users: database.collection("users"),
            login_requests: database.collection("login_requests"),
            sessions: database.collection("sessions"),
            entitlements: database.collection("entitlements"),
            sync_changes: database.collection("sync_changes"),
            telemetry_uploads: database.collection("telemetry_uploads"),
            face_analysis_frames: database.collection("face_analysis_frames"),
            backups: database.collection("backups"),
            counters: database.collection("counters"),
            admin_sessions: database.collection("admin_sessions"),
            admin_actions: database.collection("admin_actions"),
            admin_access_audit: database.collection("admin_access_audit"),
            admin_crash_reports: database.collection("admin_crash_reports"),
            admin_api_metrics: database.collection("admin_api_metrics"),
            admin_sync_metrics: database.collection("admin_sync_metrics"),
            admin_templates: database.collection("admin_templates"),
            admin_telemetry: database.collection("admin_telemetry"),
            admin_releases: database.collection("admin_releases"),
            admin_security_allowlist: database.collection("admin_security_allowlist"),
        };
        store.ensure_indexes().await?;
        store.ensure_admin_defaults().await?;
        Ok(store)
    }

    async fn ensure_indexes(&self) -> Result<(), ApiError> {
        self.users
            .create_index(unique_index("email_unique", doc! { "email": 1 }), None)
            .await
            .map_err(database_error)?;
        self.sessions
            .create_index(index("session_expiry", doc! { "expiresAt": 1 }), None)
            .await
            .map_err(database_error)?;
        self.login_requests
            .create_index(index("login_request_expiry", doc! { "expiresAt": 1 }), None)
            .await
            .map_err(database_error)?;
        self.entitlements
            .create_index(
                index("entitlement_user", doc! { "userId": 1, "purchasedAt": -1 }),
                None,
            )
            .await
            .map_err(database_error)?;
        self.sync_changes
            .create_index(
                index("sync_user_cursor", doc! { "userId": 1, "cursor": 1 }),
                None,
            )
            .await
            .map_err(database_error)?;
        self.telemetry_uploads
            .create_index(
                index(
                    "telemetry_user_received",
                    doc! { "userId": 1, "receivedAt": -1 },
                ),
                None,
            )
            .await
            .map_err(database_error)?;
        self.face_analysis_frames
            .create_index(
                index(
                    "face_analysis_user_received",
                    doc! { "userId": 1, "receivedAt": -1 },
                ),
                None,
            )
            .await
            .map_err(database_error)?;
        self.backups
            .create_index(
                index(
                    "backup_user_uploaded",
                    doc! { "userId": 1, "uploadedAt": -1 },
                ),
                None,
            )
            .await
            .map_err(database_error)?;
        self.admin_sessions
            .create_index(index("admin_session_expiry", doc! { "expiresAt": 1 }), None)
            .await
            .map_err(database_error)?;
        self.admin_sessions
            .create_index(
                unique_index("admin_refresh_unique", doc! { "refreshToken": 1 }),
                None,
            )
            .await
            .map_err(database_error)?;
        self.admin_actions
            .create_index(
                index("admin_action_recorded", doc! { "recordedAt": -1 }),
                None,
            )
            .await
            .map_err(database_error)?;
        self.admin_access_audit
            .create_index(index("admin_access_at", doc! { "at": -1 }), None)
            .await
            .map_err(database_error)?;
        self.admin_crash_reports
            .create_index(
                index("admin_crash_last_seen", doc! { "lastSeenAt": -1 }),
                None,
            )
            .await
            .map_err(database_error)?;
        self.admin_releases
            .create_index(
                unique_index("admin_release_version", doc! { "versionCode": 1 }),
                None,
            )
            .await
            .map_err(database_error)?;
        self.admin_security_allowlist
            .create_index(
                unique_index(
                    "admin_allowlist_origin",
                    doc! { "origin": 1, "protocol": 1 },
                ),
                None,
            )
            .await
            .map_err(database_error)?;
        Ok(())
    }

    async fn ensure_admin_defaults(&self) -> Result<(), ApiError> {
        self.admin_templates
            .update_one(
                doc! { "_id": "clear-sky" },
                doc! {
                    "$setOnInsert": mongodb::bson::to_document(&AdminTemplateRecord {
                        id: "clear-sky".to_owned(),
                        name: "Clear sky".to_owned(),
                        tier: "PRO".to_owned(),
                        countdown_style: "circle".to_owned(),
                        color: "#2563EB".to_owned(),
                        locales: vec!["en".to_owned(), "zh".to_owned()],
                        layout_json: json!({ "countdownStyle": "circle", "showSkipButton": true }),
                        updated_at: 0,
                    }).map_err(|_| ApiError::Internal)?,
                },
                UpdateOptions::builder().upsert(true).build(),
            )
            .await
            .map_err(database_error)?;
        self.admin_security_allowlist
            .update_one(
                doc! { "_id": "eye-http" },
                doc! {
                    "$setOnInsert": mongodb::bson::to_document(&AdminSecurityAllowlistRecord {
                        id: "eye-http".to_owned(),
                        origin: "eye.chloemlla.com".to_owned(),
                        protocol: "http".to_owned(),
                        risk: "temporary".to_owned(),
                        updated_at: 0,
                    }).map_err(|_| ApiError::Internal)?,
                },
                UpdateOptions::builder().upsert(true).build(),
            )
            .await
            .map_err(database_error)?;
        self.admin_releases
            .update_one(
                doc! { "_id": "bootstrap-release" },
                doc! {
                    "$setOnInsert": mongodb::bson::to_document(&AdminReleaseRecord {
                        id: "bootstrap-release".to_owned(),
                        version_code: 0,
                        version_name: "unregistered".to_owned(),
                        sha256: "pending".to_owned(),
                        rollout: "blocked".to_owned(),
                        force_update: false,
                        created_at: 0,
                    }).map_err(|_| ApiError::Internal)?,
                },
                UpdateOptions::builder().upsert(true).build(),
            )
            .await
            .map_err(database_error)?;
        Ok(())
    }
}

pub(crate) fn database_error(error: mongodb::error::Error) -> ApiError {
    tracing::error!(%error, "MongoDB operation failed");
    ApiError::Internal
}

fn index(name: &str, keys: mongodb::bson::Document) -> IndexModel {
    IndexModel::builder()
        .keys(keys)
        .options(IndexOptions::builder().name(name.to_owned()).build())
        .build()
}

fn unique_index(name: &str, keys: mongodb::bson::Document) -> IndexModel {
    IndexModel::builder()
        .keys(keys)
        .options(
            IndexOptions::builder()
                .name(name.to_owned())
                .unique(true)
                .build(),
        )
        .build()
}
