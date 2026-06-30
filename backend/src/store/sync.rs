use super::{
    database_error,
    documents::{CounterRecord, StoredSyncChange},
    time::now_millis,
    AppStore,
};
use crate::{
    error::ApiError,
    models::{SyncChangesResponse, SyncPushRequest, SyncPushResponse},
};
use futures_util::TryStreamExt;
use mongodb::{
    bson::doc,
    options::{FindOneAndUpdateOptions, FindOptions, ReturnDocument},
};
use uuid::Uuid;

impl AppStore {
    pub async fn push_changes(
        &self,
        user_id: &str,
        request: SyncPushRequest,
    ) -> Result<SyncPushResponse, ApiError> {
        let mut last_cursor = request.cursor.unwrap_or_default() as i64;
        let mut accepted = 0usize;

        for mut change in request.changes {
            if change.device_installation_id.trim().is_empty() {
                change.device_installation_id = request.device_installation_id.clone();
            }
            last_cursor = self.next_sync_cursor().await?;
            self.sync_changes
                .insert_one(
                    StoredSyncChange {
                        id: Uuid::new_v4().to_string(),
                        user_id: user_id.to_owned(),
                        cursor: last_cursor,
                        change,
                    },
                    None,
                )
                .await
                .map_err(database_error)?;
            accepted += 1;
        }

        Ok(SyncPushResponse {
            accepted,
            next_cursor: last_cursor.max(0) as u64,
        })
    }

    pub async fn changes_since(
        &self,
        user_id: &str,
        since: u64,
    ) -> Result<SyncChangesResponse, ApiError> {
        let options = FindOptions::builder().sort(doc! { "cursor": 1 }).build();
        let stored_changes: Vec<StoredSyncChange> = self
            .sync_changes
            .find(
                doc! {
                    "userId": user_id,
                    "cursor": { "$gt": since as i64 },
                },
                options,
            )
            .await
            .map_err(database_error)?
            .try_collect()
            .await
            .map_err(database_error)?;
        let next_cursor = stored_changes
            .iter()
            .map(|change| change.cursor)
            .max()
            .unwrap_or(since as i64);
        let changes = stored_changes
            .into_iter()
            .map(|change| change.change)
            .collect();

        Ok(SyncChangesResponse {
            changes,
            next_cursor: next_cursor.max(0) as u64,
            server_time: now_millis(),
        })
    }

    async fn next_sync_cursor(&self) -> Result<i64, ApiError> {
        let options = FindOneAndUpdateOptions::builder()
            .upsert(true)
            .return_document(ReturnDocument::After)
            .build();
        let counter = self
            .counters
            .find_one_and_update(
                doc! { "_id": SYNC_COUNTER_ID },
                doc! { "$inc": { "value": 1_i64 } },
                options,
            )
            .await
            .map_err(database_error)?
            .ok_or(ApiError::Internal)?;
        Ok(counter.value)
    }
}

const SYNC_COUNTER_ID: &str = "sync_changes";
