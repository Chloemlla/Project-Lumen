use super::{database_error, documents::StoredSyncChange, time::now_millis, AppStore};
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
        let change_count = request.changes.len();
        if change_count == 0 {
            return Ok(SyncPushResponse {
                accepted: 0,
                next_cursor: request.cursor.unwrap_or_default(),
            });
        }

        let last_cursor = self.reserve_sync_cursors(change_count as i64).await?;
        let first_cursor = last_cursor - change_count as i64 + 1;
        let default_device_installation_id = request.device_installation_id;
        let mut records = Vec::with_capacity(change_count);

        for (index, mut change) in request.changes.into_iter().enumerate() {
            if change.device_installation_id.trim().is_empty() {
                change.device_installation_id = default_device_installation_id.clone();
            }
            records.push(StoredSyncChange {
                id: Uuid::new_v4().to_string(),
                user_id: user_id.to_owned(),
                cursor: first_cursor + index as i64,
                change,
            });
        }

        self.sync_changes
            .insert_many(records, None)
            .await
            .map_err(database_error)?;

        Ok(SyncPushResponse {
            accepted: change_count,
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

    async fn reserve_sync_cursors(&self, count: i64) -> Result<i64, ApiError> {
        let options = FindOneAndUpdateOptions::builder()
            .upsert(true)
            .return_document(ReturnDocument::After)
            .build();
        let counter = self
            .counters
            .find_one_and_update(
                doc! { "_id": SYNC_COUNTER_ID },
                doc! { "$inc": { "value": count } },
                options,
            )
            .await
            .map_err(database_error)?
            .ok_or(ApiError::Internal)?;
        Ok(counter.value)
    }
}

const SYNC_COUNTER_ID: &str = "sync_changes";
