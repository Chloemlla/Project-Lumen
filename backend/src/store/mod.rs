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
    AdminTelemetryRecord, AdminTemplateRecord, ApiNonceRecord, BackupRecord, CounterRecord,
    EntitlementRecord, PendingLogin, SessionRecord, StoredSyncChange,
};
use face_analysis::FaceAnalysisFrameRecord;
use mongodb::{
    bson::doc,
    options::{ClientOptions, IndexOptions, UpdateOptions},
    Client, Collection, IndexModel,
};
use serde_json::json;
use std::time::{Duration, Instant};
use telemetry::TelemetryUploadRecord;

pub use documents::UserRecord;

pub struct AppStore {
    pub(crate) users: Collection<UserRecord>,
    pub(crate) login_requests: Collection<PendingLogin>,
    pub(crate) sessions: Collection<SessionRecord>,
    pub(crate) api_nonces: Collection<ApiNonceRecord>,
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
        let started_at = Instant::now();
        tracing::info!(
            phase = "mongodb.options.parse",
            mongodb_uri = %config.redacted_mongodb_uri(),
            mongodb_database = %config.mongodb_database,
            "parsing MongoDB client options"
        );
        let client_options = ClientOptions::parse(&config.mongodb_uri)
            .await
            .map_err(|error| startup_database_error("mongodb.options.parse", error))?;
        tracing::info!(
            phase = "mongodb.options.parse",
            elapsed_ms = elapsed_ms(&started_at),
            "MongoDB client options parsed"
        );

        tracing::info!(phase = "mongodb.client.create", "creating MongoDB client");
        let client = Client::with_options(client_options)
            .map_err(|error| startup_database_error("mongodb.client.create", error))?;
        tracing::info!(
            phase = "mongodb.client.create",
            elapsed_ms = elapsed_ms(&started_at),
            "MongoDB client created"
        );

        let database = client.database(&config.mongodb_database);
        tracing::info!(
            phase = "mongodb.database.select",
            mongodb_database = %config.mongodb_database,
            elapsed_ms = elapsed_ms(&started_at),
            "MongoDB database selected"
        );
        let store = Self {
            users: database.collection("users"),
            login_requests: database.collection("login_requests"),
            sessions: database.collection("sessions"),
            api_nonces: database.collection("api_nonces"),
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
        tracing::info!(
            phase = "mongodb.collections.init",
            elapsed_ms = elapsed_ms(&started_at),
            "MongoDB collection handles initialized"
        );
        store.ensure_indexes().await?;
        store.ensure_admin_defaults().await?;
        tracing::info!(
            phase = "mongodb.store.ready",
            elapsed_ms = elapsed_ms(&started_at),
            "MongoDB store initialization completed"
        );
        Ok(store)
    }

    async fn ensure_indexes(&self) -> Result<(), ApiError> {
        let started_at = Instant::now();
        tracing::info!(phase = "mongodb.indexes.ensure", "ensuring MongoDB indexes");
        ensure_index(
            &self.users,
            "users",
            unique_index("email_unique", doc! { "email": 1 }),
        )
        .await?;
        ensure_index(
            &self.sessions,
            "sessions",
            index("session_expiry", doc! { "expiresAt": 1 }),
        )
        .await?;
        ensure_index(
            &self.sessions,
            "sessions",
            sparse_unique_index("session_refresh_unique", doc! { "refreshToken": 1 }),
        )
        .await?;
        ensure_index(
            &self.api_nonces,
            "api_nonces",
            ttl_index("api_nonce_expiry", doc! { "expiresAt": 1 }),
        )
        .await?;
        ensure_index(
            &self.login_requests,
            "login_requests",
            index("login_request_expiry", doc! { "expiresAt": 1 }),
        )
        .await?;
        ensure_index(
            &self.entitlements,
            "entitlements",
            index("entitlement_user", doc! { "userId": 1, "purchasedAt": -1 }),
        )
        .await?;
        ensure_index(
            &self.entitlements,
            "entitlements",
            index(
                "entitlement_purchased_recent",
                doc! { "purchasedAt": -1, "_id": -1 },
            ),
        )
        .await?;
        ensure_index(
            &self.entitlements,
            "entitlements",
            index(
                "entitlement_google_last_verified",
                doc! { "source": 1, "lastVerifiedAt": -1 },
            ),
        )
        .await?;
        ensure_index(
            &self.sync_changes,
            "sync_changes",
            index("sync_user_cursor", doc! { "userId": 1, "cursor": 1 }),
        )
        .await?;
        ensure_index(
            &self.sync_changes,
            "sync_changes",
            index("sync_cursor_recent", doc! { "cursor": -1 }),
        )
        .await?;
        ensure_index(
            &self.telemetry_uploads,
            "telemetry_uploads",
            index(
                "telemetry_user_received",
                doc! { "userId": 1, "receivedAt": -1 },
            ),
        )
        .await?;
        ensure_index(
            &self.telemetry_uploads,
            "telemetry_uploads",
            index(
                "telemetry_source_received",
                doc! { "payload.sourceApp": 1, "receivedAt": -1 },
            ),
        )
        .await?;
        ensure_index(
            &self.face_analysis_frames,
            "face_analysis_frames",
            index(
                "face_analysis_user_received",
                doc! { "userId": 1, "receivedAt": -1 },
            ),
        )
        .await?;
        ensure_index(
            &self.backups,
            "backups",
            index(
                "backup_user_uploaded",
                doc! { "userId": 1, "uploadedAt": -1 },
            ),
        )
        .await?;
        ensure_index(
            &self.backups,
            "backups",
            index("backup_uploaded_recent", doc! { "uploadedAt": -1, "_id": -1 }),
        )
        .await?;
        ensure_index(
            &self.admin_sessions,
            "admin_sessions",
            index("admin_session_expiry", doc! { "expiresAt": 1 }),
        )
        .await?;
        ensure_index(
            &self.admin_sessions,
            "admin_sessions",
            unique_index("admin_refresh_unique", doc! { "refreshToken": 1 }),
        )
        .await?;
        ensure_index(
            &self.admin_actions,
            "admin_actions",
            index("admin_action_recorded", doc! { "recordedAt": -1 }),
        )
        .await?;
        ensure_index(
            &self.admin_access_audit,
            "admin_access_audit",
            index("admin_access_at", doc! { "at": -1 }),
        )
        .await?;
        ensure_index(
            &self.admin_crash_reports,
            "admin_crash_reports",
            index("admin_crash_last_seen", doc! { "lastSeenAt": -1 }),
        )
        .await?;
        ensure_index(
            &self.admin_api_metrics,
            "admin_api_metrics",
            index("admin_api_metric_sampled", doc! { "sampledAt": -1 }),
        )
        .await?;
        ensure_index(
            &self.admin_sync_metrics,
            "admin_sync_metrics",
            index("admin_sync_metric_sampled", doc! { "sampledAt": -1 }),
        )
        .await?;
        ensure_index(
            &self.admin_templates,
            "admin_templates",
            index("admin_template_updated", doc! { "updatedAt": -1 }),
        )
        .await?;
        ensure_index(
            &self.admin_telemetry,
            "admin_telemetry",
            index("admin_telemetry_sampled", doc! { "sampledAt": -1 }),
        )
        .await?;
        ensure_index(
            &self.admin_releases,
            "admin_releases",
            unique_index("admin_release_version", doc! { "versionCode": 1 }),
        )
        .await?;
        ensure_index(
            &self.admin_security_allowlist,
            "admin_security_allowlist",
            unique_index(
                "admin_allowlist_origin",
                doc! { "origin": 1, "protocol": 1 },
            ),
        )
        .await?;
        tracing::info!(
            phase = "mongodb.indexes.ensure",
            elapsed_ms = elapsed_ms(&started_at),
            "MongoDB indexes ensured"
        );
        Ok(())
    }

    async fn ensure_admin_defaults(&self) -> Result<(), ApiError> {
        let started_at = Instant::now();
        tracing::info!(
            phase = "mongodb.defaults.ensure",
            "ensuring MongoDB admin defaults"
        );

        let default_started_at = Instant::now();
        tracing::debug!(
            phase = "mongodb.default.ensure",
            collection = "admin_templates",
            default_id = "clear-sky",
            "ensuring admin template default"
        );
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
                    }).map_err(|error| {
                        tracing::error!(
                            phase = "mongodb.default.serialize",
                            collection = "admin_templates",
                            default_id = "clear-sky",
                            %error,
                            "failed to serialize admin template default"
                        );
                        ApiError::Internal
                    })?,
                },
                UpdateOptions::builder().upsert(true).build(),
            )
            .await
            .map_err(|error| startup_database_error("mongodb.default.ensure", error))?;
        tracing::info!(
            phase = "mongodb.default.ensure",
            collection = "admin_templates",
            default_id = "clear-sky",
            elapsed_ms = elapsed_ms(&default_started_at),
            "admin template default ensured"
        );

        let default_started_at = Instant::now();
        tracing::debug!(
            phase = "mongodb.default.ensure",
            collection = "admin_security_allowlist",
            default_id = "eye-https",
            "ensuring admin security allowlist default"
        );
        self.admin_security_allowlist
            .update_one(
                doc! { "_id": "eye-https" },
                doc! {
                    "$setOnInsert": mongodb::bson::to_document(&AdminSecurityAllowlistRecord {
                        id: "eye-https".to_owned(),
                        origin: "eye.chloemlla.com".to_owned(),
                        protocol: "https".to_owned(),
                        risk: "approved".to_owned(),
                        updated_at: 0,
                    }).map_err(|error| {
                        tracing::error!(
                            phase = "mongodb.default.serialize",
                            collection = "admin_security_allowlist",
                            default_id = "eye-https",
                            %error,
                            "failed to serialize admin security allowlist default"
                        );
                        ApiError::Internal
                    })?,
                },
                UpdateOptions::builder().upsert(true).build(),
            )
            .await
            .map_err(|error| startup_database_error("mongodb.default.ensure", error))?;
        tracing::info!(
            phase = "mongodb.default.ensure",
            collection = "admin_security_allowlist",
            default_id = "eye-https",
            elapsed_ms = elapsed_ms(&default_started_at),
            "admin security allowlist default ensured"
        );

        let default_started_at = Instant::now();
        tracing::debug!(
            phase = "mongodb.default.ensure",
            collection = "admin_releases",
            default_id = "bootstrap-release",
            "ensuring admin release default"
        );
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
                    }).map_err(|error| {
                        tracing::error!(
                            phase = "mongodb.default.serialize",
                            collection = "admin_releases",
                            default_id = "bootstrap-release",
                            %error,
                            "failed to serialize admin release default"
                        );
                        ApiError::Internal
                    })?,
                },
                UpdateOptions::builder().upsert(true).build(),
            )
            .await
            .map_err(|error| startup_database_error("mongodb.default.ensure", error))?;
        tracing::info!(
            phase = "mongodb.default.ensure",
            collection = "admin_releases",
            default_id = "bootstrap-release",
            elapsed_ms = elapsed_ms(&default_started_at),
            "admin release default ensured"
        );
        tracing::info!(
            phase = "mongodb.defaults.ensure",
            elapsed_ms = elapsed_ms(&started_at),
            "MongoDB admin defaults ensured"
        );
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

fn sparse_unique_index(name: &str, keys: mongodb::bson::Document) -> IndexModel {
    IndexModel::builder()
        .keys(keys)
        .options(
            IndexOptions::builder()
                .name(name.to_owned())
                .unique(true)
                .sparse(true)
                .build(),
        )
        .build()
}

fn ttl_index(name: &str, keys: mongodb::bson::Document) -> IndexModel {
    IndexModel::builder()
        .keys(keys)
        .options(
            IndexOptions::builder()
                .name(name.to_owned())
                .expire_after(Duration::from_secs(0))
                .build(),
        )
        .build()
}

async fn ensure_index<T>(
    collection: &Collection<T>,
    collection_name: &'static str,
    model: IndexModel,
) -> Result<(), ApiError>
where
    T: Send + Sync,
{
    let index_name = model
        .options
        .as_ref()
        .and_then(|options| options.name.as_deref())
        .unwrap_or("<unnamed>")
        .to_owned();
    let started_at = Instant::now();
    tracing::debug!(
        phase = "mongodb.index.ensure",
        collection = collection_name,
        index = %index_name,
        "ensuring MongoDB index"
    );
    collection
        .create_index(model, None)
        .await
        .map_err(|error| startup_database_error("mongodb.index.ensure", error))?;
    tracing::info!(
        phase = "mongodb.index.ensure",
        collection = collection_name,
        index = %index_name,
        elapsed_ms = elapsed_ms(&started_at),
        "MongoDB index ensured"
    );
    Ok(())
}

fn startup_database_error(phase: &'static str, error: mongodb::error::Error) -> ApiError {
    tracing::error!(
        phase = phase,
        %error,
        "MongoDB startup operation failed"
    );
    ApiError::Internal
}

fn elapsed_ms(started_at: &Instant) -> u64 {
    started_at.elapsed().as_millis().min(u128::from(u64::MAX)) as u64
}
