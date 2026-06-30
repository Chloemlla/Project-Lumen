mod auth;
mod backups;
mod documents;
mod entitlements;
mod sync;
mod time;

use crate::{config::Config, error::ApiError};
use documents::{
    BackupRecord, CounterRecord, EntitlementRecord, PendingLogin, SessionRecord, StoredSyncChange,
};
use mongodb::{
    bson::doc,
    options::{ClientOptions, IndexOptions},
    Client, Collection, IndexModel,
};

pub use documents::UserRecord;

pub struct AppStore {
    pub(crate) users: Collection<UserRecord>,
    pub(crate) login_requests: Collection<PendingLogin>,
    pub(crate) sessions: Collection<SessionRecord>,
    pub(crate) entitlements: Collection<EntitlementRecord>,
    pub(crate) sync_changes: Collection<StoredSyncChange>,
    pub(crate) backups: Collection<BackupRecord>,
    pub(crate) counters: Collection<CounterRecord>,
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
            backups: database.collection("backups"),
            counters: database.collection("counters"),
        };
        store.ensure_indexes().await?;
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
