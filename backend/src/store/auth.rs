use super::{
    database_error,
    documents::{ApiNonceRecord, PendingLogin, SessionRecord, UserRecord},
    time::{now_millis, ttl_seconds_to_millis},
    AppStore,
};
use crate::{
    error::ApiError,
    models::{AuthSessionResponse, RefreshSessionRequest, StartEmailResponse, VerifyEmailRequest},
};
use mongodb::bson::doc;
use uuid::Uuid;

impl AppStore {
    pub async fn start_email_login(
        &self,
        email: &str,
        code: &str,
        ttl_seconds: u64,
    ) -> Result<StartEmailResponse, ApiError> {
        let email = normalize_email(email)?;
        let request_id = Uuid::new_v4().to_string();
        let expires_at = now_millis() + ttl_seconds_to_millis(ttl_seconds);

        self.login_requests
            .insert_one(
                PendingLogin {
                    request_id: request_id.clone(),
                    email,
                    code: code.to_owned(),
                    expires_at,
                },
                None,
            )
            .await
            .map_err(database_error)?;

        Ok(StartEmailResponse {
            request_id,
            expires_at,
            delivery: "development_code".to_owned(),
            dev_code: Some(code.to_owned()),
        })
    }

    pub async fn verify_email_login(
        &self,
        request: VerifyEmailRequest,
        access_token_ttl_seconds: u64,
        refresh_token_ttl_seconds: u64,
    ) -> Result<AuthSessionResponse, ApiError> {
        let email = normalize_email(&request.email)?;
        let pending = self
            .login_requests
            .find_one_and_delete(doc! { "_id": &request.request_id }, None)
            .await
            .map_err(database_error)?
            .ok_or_else(|| ApiError::BadRequest("Login request was not found.".to_owned()))?;

        if pending.email != email
            || pending.code != request.code
            || pending.expires_at < now_millis()
        {
            return Err(ApiError::Unauthorized);
        }

        let user = self
            .upsert_user(email, request.device_installation_id.unwrap_or_default())
            .await?;
        self.create_session_response(user, access_token_ttl_seconds, refresh_token_ttl_seconds)
            .await
    }

    pub async fn refresh_session(
        &self,
        request: RefreshSessionRequest,
        access_token_ttl_seconds: u64,
        refresh_token_ttl_seconds: u64,
    ) -> Result<AuthSessionResponse, ApiError> {
        let session = self
            .sessions
            .find_one(doc! { "refreshToken": &request.refresh_token }, None)
            .await
            .map_err(database_error)?
            .ok_or(ApiError::Unauthorized)?;

        if session.refresh_expires_at < now_millis() {
            self.sessions
                .delete_one(doc! { "refreshToken": &request.refresh_token }, None)
                .await
                .map_err(database_error)?;
            return Err(ApiError::Unauthorized);
        }

        let mut user = self
            .users
            .find_one(doc! { "_id": session.user_id }, None)
            .await
            .map_err(database_error)?
            .ok_or(ApiError::Unauthorized)?;

        if let Some(device_installation_id) = request.device_installation_id {
            if !device_installation_id.trim().is_empty()
                && user.device_installation_id != device_installation_id
            {
                self.users
                    .update_one(
                        doc! { "_id": &user.id },
                        doc! { "$set": { "deviceInstallationId": &device_installation_id } },
                        None,
                    )
                    .await
                    .map_err(database_error)?;
                user.device_installation_id = device_installation_id;
            }
        }

        self.sessions
            .delete_one(doc! { "refreshToken": &request.refresh_token }, None)
            .await
            .map_err(database_error)?;

        self.create_session_response(user, access_token_ttl_seconds, refresh_token_ttl_seconds)
            .await
    }

    pub async fn remember_api_nonce(&self, nonce: &str, expires_at: i64) -> Result<(), ApiError> {
        self.api_nonces
            .insert_one(
                ApiNonceRecord {
                    nonce: nonce.to_owned(),
                    expires_at,
                },
                None,
            )
            .await
            .map_err(|error| {
                if is_duplicate_key(&error) {
                    ApiError::Forbidden
                } else {
                    database_error(error)
                }
            })?;
        Ok(())
    }

    async fn create_session_response(
        &self,
        user: UserRecord,
        access_token_ttl_seconds: u64,
        refresh_token_ttl_seconds: u64,
    ) -> Result<AuthSessionResponse, ApiError> {
        let access_token = Uuid::new_v4().to_string();
        let refresh_token = Uuid::new_v4().to_string();
        let expires_at = now_millis() + ttl_seconds_to_millis(access_token_ttl_seconds);
        let refresh_expires_at = now_millis() + ttl_seconds_to_millis(refresh_token_ttl_seconds);

        self.sessions
            .insert_one(
                SessionRecord {
                    token: access_token.clone(),
                    refresh_token: refresh_token.clone(),
                    user_id: user.id.clone(),
                    expires_at,
                    refresh_expires_at,
                },
                None,
            )
            .await
            .map_err(database_error)?;

        Ok(AuthSessionResponse {
            access_token,
            refresh_token,
            token_type: "Bearer".to_owned(),
            expires_at,
            refresh_expires_at,
            user: user.to_dto(),
        })
    }

    pub async fn user_for_token(&self, token: &str) -> Result<UserRecord, ApiError> {
        let session = self
            .sessions
            .find_one(doc! { "_id": token }, None)
            .await
            .map_err(database_error)?
            .ok_or(ApiError::Unauthorized)?;

        if session.expires_at < now_millis() {
            self.sessions
                .delete_one(doc! { "_id": token }, None)
                .await
                .map_err(database_error)?;
            return Err(ApiError::Unauthorized);
        }

        self.users
            .find_one(doc! { "_id": session.user_id }, None)
            .await
            .map_err(database_error)?
            .ok_or(ApiError::Unauthorized)
    }

    async fn upsert_user(
        &self,
        email: String,
        device_installation_id: String,
    ) -> Result<UserRecord, ApiError> {
        if let Some(mut user) = self
            .users
            .find_one(doc! { "email": &email }, None)
            .await
            .map_err(database_error)?
        {
            if !device_installation_id.trim().is_empty() {
                self.users
                    .update_one(
                        doc! { "_id": &user.id },
                        doc! { "$set": { "deviceInstallationId": &device_installation_id } },
                        None,
                    )
                    .await
                    .map_err(database_error)?;
                user.device_installation_id = device_installation_id;
            }
            return Ok(user);
        }

        let user = UserRecord {
            id: Uuid::new_v4().to_string(),
            email,
            created_at: now_millis(),
            device_installation_id,
            device_asset_model: String::new(),
            device_asset_version_code: 0,
            device_asset_last_seen_at: 0,
            device_asset_security_config: String::new(),
        };
        self.users
            .insert_one(&user, None)
            .await
            .map_err(database_error)?;
        Ok(user)
    }
}

fn is_duplicate_key(error: &mongodb::error::Error) -> bool {
    error.to_string().contains("E11000") || error.to_string().contains("duplicate key")
}

fn normalize_email(email: &str) -> Result<String, ApiError> {
    let normalized = email.trim().to_lowercase();
    if normalized.contains('@') && normalized.len() <= 254 {
        Ok(normalized)
    } else {
        Err(ApiError::BadRequest(
            "A valid email address is required.".to_owned(),
        ))
    }
}
