use crate::config::Config;
use reqwest::{Client, StatusCode};
use serde::{Deserialize, Serialize};
use std::time::Duration;
use thiserror::Error;

#[derive(Clone)]
pub struct OutEmailClient {
    base_url: String,
    api_key: String,
    from: Option<String>,
    display_name: Option<String>,
    domain: Option<String>,
    http: Client,
}

impl OutEmailClient {
    pub fn from_config(config: &Config) -> Option<Self> {
        if !config.outemail_configured() {
            return None;
        }

        let http = Client::builder()
            .timeout(Duration::from_secs(config.outemail_timeout_seconds))
            .build()
            .unwrap_or_else(|error| {
                tracing::warn!(%error, "failed to build outemail HTTP client with timeout");
                Client::new()
            });

        Some(Self {
            base_url: config
                .outemail_base_url
                .trim()
                .trim_end_matches('/')
                .to_owned(),
            api_key: config.outemail_api_key.trim().to_owned(),
            from: optional_value(&config.outemail_from),
            display_name: optional_value(&config.outemail_display_name),
            domain: optional_value(&config.outemail_domain),
            http,
        })
    }

    pub async fn send_login_code(
        &self,
        to: &str,
        code: &str,
        ttl_seconds: u64,
    ) -> Result<Option<String>, OutEmailError> {
        let url = format!("{}/api/outemail/send", self.base_url);
        let content = login_code_html(code, ttl_seconds);
        let request = SendEmailRequest {
            to,
            subject: "Project Lumen login code",
            content: &content,
            from: self.from.as_deref(),
            display_name: self.display_name.as_deref(),
            domain: self.domain.as_deref(),
        };
        let response = self
            .http
            .post(url)
            .bearer_auth(&self.api_key)
            .json(&request)
            .send()
            .await
            .map_err(OutEmailError::Request)?;

        let status = response.status();
        let body = response.text().await.map_err(OutEmailError::ReadBody)?;
        let parsed: SendEmailResponse =
            serde_json::from_str(&body).map_err(|source| OutEmailError::ParseBody {
                source,
                body: body.clone(),
            })?;

        if status.is_success() && parsed.success == Some(true) {
            Ok(parsed.message_id)
        } else {
            Err(OutEmailError::Provider {
                status,
                message: parsed.error.unwrap_or_else(|| {
                    "outemail provider returned an unsuccessful response".to_owned()
                }),
            })
        }
    }
}

#[derive(Debug, Error)]
pub enum OutEmailError {
    #[error("outemail request failed: {0}")]
    Request(reqwest::Error),
    #[error("outemail response body could not be read: {0}")]
    ReadBody(reqwest::Error),
    #[error("outemail response JSON could not be parsed: {source}; body={body}")]
    ParseBody {
        source: serde_json::Error,
        body: String,
    },
    #[error("outemail provider failed with HTTP {status}: {message}")]
    Provider { status: StatusCode, message: String },
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SendEmailRequest<'a> {
    to: &'a str,
    subject: &'a str,
    content: &'a str,
    #[serde(skip_serializing_if = "Option::is_none")]
    from: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    display_name: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    domain: Option<&'a str>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct SendEmailResponse {
    success: Option<bool>,
    message_id: Option<String>,
    error: Option<String>,
}

fn optional_value(value: &str) -> Option<String> {
    let trimmed = value.trim();
    (!trimmed.is_empty()).then(|| trimmed.to_owned())
}

fn login_code_html(code: &str, ttl_seconds: u64) -> String {
    let minutes = (ttl_seconds / 60).max(1);
    format!(
        "<p>Your Project Lumen verification code is:</p>\
         <p style=\"font-size:24px;font-weight:700;letter-spacing:4px;\">{code}</p>\
         <p>This code expires in {minutes} minutes. If you did not request it, ignore this email.</p>"
    )
}
