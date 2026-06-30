use super::{database_error, time::now_millis, AppStore};
use crate::{
    error::ApiError,
    models::{FaceAnalysisFrameUploadRequest, FaceAnalysisFrameUploadResponse},
};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct FaceAnalysisFrameRecord {
    #[serde(rename = "_id")]
    pub id: String,
    pub user_id: String,
    pub device_installation_id: String,
    pub received_at: i64,
    pub payload: FaceAnalysisFrameUploadRequest,
}

impl AppStore {
    pub async fn record_face_analysis_frame(
        &self,
        user_id: &str,
        request: FaceAnalysisFrameUploadRequest,
    ) -> Result<FaceAnalysisFrameUploadResponse, ApiError> {
        let device_installation_id = request.device_installation_id.trim().to_owned();
        if device_installation_id.is_empty() {
            return Err(ApiError::BadRequest(
                "deviceInstallationId is required for face analysis frame upload".to_owned(),
            ));
        }

        let id = Uuid::new_v4().to_string();
        let received_at = now_millis();
        self.face_analysis_frames
            .insert_one(
                FaceAnalysisFrameRecord {
                    id: id.clone(),
                    user_id: user_id.to_owned(),
                    device_installation_id,
                    received_at,
                    payload: request,
                },
                None,
            )
            .await
            .map_err(database_error)?;

        Ok(FaceAnalysisFrameUploadResponse {
            accepted: true,
            id,
            received_at,
        })
    }
}
