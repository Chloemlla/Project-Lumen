use super::{database_error, documents::AdminAccessAuditRecord, time::now_millis, AppStore};
use crate::error::ApiError;
use uuid::Uuid;

impl AppStore {
    pub async fn record_access_audit(
        &self,
        user_id: String,
        endpoint: String,
        ip: String,
        status: u16,
    ) -> Result<(), ApiError> {
        self.admin_access_audit
            .insert_one(
                AdminAccessAuditRecord {
                    id: Uuid::new_v4().to_string(),
                    at: now_millis(),
                    user_id,
                    endpoint,
                    ip,
                    geo: "unknown".to_owned(),
                    status,
                },
                None,
            )
            .await
            .map_err(database_error)?;
        Ok(())
    }
}
