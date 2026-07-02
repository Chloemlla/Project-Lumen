use super::{database_error, time::now_millis, AppStore};
use crate::{
    error::ApiError,
    models::{DeviceRegistrationRequest, DeviceRegistrationResponse},
};
use mongodb::bson::doc;

impl AppStore {
    pub async fn register_device_asset(
        &self,
        user_id: &str,
        request: DeviceRegistrationRequest,
    ) -> Result<DeviceRegistrationResponse, ApiError> {
        let device_installation_id = normalize_required(
            request.device_installation_id,
            "deviceInstallationId",
            MAX_DEVICE_INSTALLATION_ID_LENGTH,
        )?;
        let model = normalize_optional(request.model, MAX_DEVICE_MODEL_LENGTH);
        let local_security_config =
            normalize_optional(request.local_security_config, MAX_SECURITY_CONFIG_LENGTH);
        let version_code = request.version_code.max(0);
        let registered_at = now_millis();

        let update = self
            .users
            .update_one(
                doc! { "_id": user_id },
                doc! {
                    "$set": {
                        "deviceInstallationId": &device_installation_id,
                        "deviceAssetModel": model,
                        "deviceAssetVersionCode": version_code,
                        "deviceAssetLastSeenAt": registered_at,
                        "deviceAssetSecurityConfig": local_security_config,
                    },
                },
                None,
            )
            .await
            .map_err(database_error)?;
        if update.matched_count == 0 {
            return Err(ApiError::Unauthorized);
        }

        Ok(DeviceRegistrationResponse {
            accepted: true,
            device_installation_id,
            registered_at,
        })
    }
}

fn normalize_required(value: String, field: &str, max_chars: usize) -> Result<String, ApiError> {
    let normalized = normalize_optional(value, max_chars);
    if normalized.is_empty() {
        return Err(ApiError::BadRequest(format!("{field} is required.")));
    }
    Ok(normalized)
}

fn normalize_optional(value: String, max_chars: usize) -> String {
    value.trim().chars().take(max_chars).collect()
}

const MAX_DEVICE_INSTALLATION_ID_LENGTH: usize = 128;
const MAX_DEVICE_MODEL_LENGTH: usize = 160;
const MAX_SECURITY_CONFIG_LENGTH: usize = 160;
