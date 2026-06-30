use super::{
    database_error,
    documents::BackupRecord,
    time::now_millis,
    AppStore,
};
use crate::{
    error::ApiError,
    models::{BackupDocument, BackupMetadata, BackupUploadRequest},
};
use mongodb::{bson::doc, options::FindOneOptions};
use uuid::Uuid;

impl AppStore {
    pub async fn save_backup(
        &self,
        user_id: &str,
        request: BackupUploadRequest,
    ) -> Result<BackupMetadata, ApiError> {
        let uploaded_at = now_millis();
        let record = BackupRecord {
            id: Uuid::new_v4().to_string(),
            user_id: user_id.to_owned(),
            device_installation_id: request.device_installation_id,
            schema_version: request.schema_version,
            exported_at: request.exported_at,
            uploaded_at,
            backup: request.backup,
        };
        let metadata = record.to_metadata();

        self.backups.insert_one(record, None).await.map_err(database_error)?;
        Ok(metadata)
    }

    pub async fn latest_backup(&self, user_id: &str) -> Result<Option<BackupDocument>, ApiError> {
        let options = FindOneOptions::builder()
            .sort(doc! { "uploadedAt": -1, "_id": -1 })
            .build();
        let backup = self
            .backups
            .find_one(doc! { "userId": user_id }, options)
            .await
            .map_err(database_error)?
            .map(BackupRecord::to_document);
        Ok(backup)
    }
}

impl BackupRecord {
    fn to_metadata(&self) -> BackupMetadata {
        BackupMetadata {
            id: self.id.clone(),
            device_installation_id: self.device_installation_id.clone(),
            schema_version: self.schema_version,
            exported_at: self.exported_at,
            uploaded_at: self.uploaded_at,
        }
    }

    fn to_document(self) -> BackupDocument {
        BackupDocument {
            metadata: self.to_metadata(),
            backup: self.backup,
        }
    }
}
