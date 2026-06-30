use super::{
    database_error,
    documents::AdminSessionRecord,
    time::{now_millis, ttl_seconds_to_millis},
    AppStore,
};
use crate::{
    config::Config,
    error::ApiError,
    models::{AdminOperatorDto, AdminSessionResponse},
};
use mongodb::bson::doc;
use uuid::Uuid;

impl AppStore {
    pub async fn create_admin_session(
        &self,
        username: &str,
        password: &str,
        config: &Config,
    ) -> Result<AdminSessionResponse, ApiError> {
        if username != config.admin_username || password != config.admin_password {
            return Err(ApiError::Unauthorized);
        }
        self.issue_admin_session(username, config).await
    }

    pub async fn refresh_admin_session(
        &self,
        refresh_token: &str,
        config: &Config,
    ) -> Result<AdminSessionResponse, ApiError> {
        let session = self
            .admin_sessions
            .find_one_and_delete(doc! { "refreshToken": refresh_token }, None)
            .await
            .map_err(database_error)?
            .ok_or(ApiError::Unauthorized)?;

        if session.refresh_expires_at < now_millis() {
            return Err(ApiError::Unauthorized);
        }

        self.issue_admin_session(&session.username, config).await
    }

    pub async fn admin_operator_for_token(
        &self,
        access_token: &str,
    ) -> Result<AdminOperatorDto, ApiError> {
        let session = self
            .admin_sessions
            .find_one(doc! { "_id": access_token }, None)
            .await
            .map_err(database_error)?
            .ok_or(ApiError::Unauthorized)?;

        if session.expires_at < now_millis() {
            self.admin_sessions
                .delete_one(doc! { "_id": access_token }, None)
                .await
                .map_err(database_error)?;
            return Err(ApiError::Unauthorized);
        }

        Ok(AdminOperatorDto {
            id: session.username.clone(),
            username: session.username,
            role: session.role,
        })
    }

    async fn issue_admin_session(
        &self,
        username: &str,
        config: &Config,
    ) -> Result<AdminSessionResponse, ApiError> {
        let now = now_millis();
        let access_token = Uuid::new_v4().to_string();
        let refresh_token = Uuid::new_v4().to_string();
        let expires_at = now + ttl_seconds_to_millis(config.admin_access_token_ttl_seconds);
        let refresh_expires_at =
            now + ttl_seconds_to_millis(config.admin_refresh_token_ttl_seconds);
        let operator = AdminOperatorDto {
            id: username.to_owned(),
            username: username.to_owned(),
            role: "admin".to_owned(),
        };

        self.admin_sessions
            .insert_one(
                AdminSessionRecord {
                    access_token: access_token.clone(),
                    refresh_token: refresh_token.clone(),
                    username: username.to_owned(),
                    role: operator.role.clone(),
                    expires_at,
                    refresh_expires_at,
                    created_at: now,
                },
                None,
            )
            .await
            .map_err(database_error)?;

        Ok(AdminSessionResponse {
            access_token,
            refresh_token,
            token_type: "Bearer".to_owned(),
            expires_at,
            refresh_expires_at,
            operator,
        })
    }
}
