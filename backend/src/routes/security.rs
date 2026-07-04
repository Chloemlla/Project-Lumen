use crate::{error::ApiError, state::AppState};
use axum::{
    body::{to_bytes, Body},
    extract::State,
    http::{HeaderMap, Request},
    middleware::Next,
    response::Response,
};
use hmac::{Hmac, Mac};
use sha2::{Digest, Sha256};
use std::time::{SystemTime, UNIX_EPOCH};
use subtle::ConstantTimeEq;

type HmacSha256 = Hmac<Sha256>;

const MAX_SIGNED_BODY_BYTES: usize = 10 * 1024 * 1024;
const HEADER_TIMESTAMP: &str = "x-lumen-timestamp";
const HEADER_NONCE: &str = "x-lumen-nonce";
const HEADER_SIGNATURE: &str = "x-lumen-signature";

pub async fn enforce_api_security(
    State(state): State<AppState>,
    request: Request<Body>,
    next: Next,
) -> Result<Response, ApiError> {
    if !state.config.require_request_signing {
        return Ok(next.run(request).await);
    }

    let (parts, body) = request.into_parts();
    let body_bytes = to_bytes(body, MAX_SIGNED_BODY_BYTES)
        .await
        .map_err(|_| ApiError::BadRequest("Request body is too large.".to_owned()))?;

    let timestamp = required_header(
        &parts.headers,
        HEADER_TIMESTAMP,
        "REQUEST_SIGNATURE_TIMESTAMP_MISSING",
        "Missing X-Lumen-Timestamp request signing header.",
        "REQUEST_SIGNATURE_TIMESTAMP_INVALID",
        "X-Lumen-Timestamp request signing header is not valid text.",
    )?;
    let nonce = required_header(
        &parts.headers,
        HEADER_NONCE,
        "REQUEST_SIGNATURE_NONCE_MISSING",
        "Missing X-Lumen-Nonce request signing header.",
        "REQUEST_SIGNATURE_NONCE_INVALID",
        "X-Lumen-Nonce request signing header is not valid text.",
    )?;
    let signature = required_header(
        &parts.headers,
        HEADER_SIGNATURE,
        "REQUEST_SIGNATURE_MISSING",
        "Missing X-Lumen-Signature request signing header.",
        "REQUEST_SIGNATURE_HEADER_INVALID",
        "X-Lumen-Signature request signing header is not valid text.",
    )?;
    validate_timestamp(timestamp, state.config.request_timestamp_skew_seconds)?;

    let canonical = canonical_payload(
        parts.method.as_str(),
        parts.uri.path(),
        parts.uri.query().unwrap_or_default(),
        &body_bytes,
        timestamp,
        nonce,
    );
    validate_signature(&state.config.request_signing_secret, &canonical, signature)?;

    let expires_at = now_millis() + (state.config.request_timestamp_skew_seconds as i64 * 1_000);
    state.store.remember_api_nonce(nonce, expires_at).await?;

    Ok(next
        .run(Request::from_parts(parts, Body::from(body_bytes)))
        .await)
}

fn required_header<'a>(
    headers: &'a HeaderMap,
    name: &str,
    missing_reason_code: &'static str,
    missing_message: &'static str,
    invalid_reason_code: &'static str,
    invalid_message: &'static str,
) -> Result<&'a str, ApiError> {
    let value = headers.get(name).ok_or_else(|| {
        ApiError::forbidden_reason(missing_reason_code, missing_message)
    })?;
    let value = value
        .to_str()
        .map(str::trim)
        .map_err(|_| ApiError::forbidden_reason(invalid_reason_code, invalid_message))?;
    if value.is_empty() {
        return Err(ApiError::forbidden_reason(
            missing_reason_code,
            missing_message,
        ));
    }
    Ok(value)
}

fn validate_timestamp(timestamp: &str, allowed_skew_seconds: u64) -> Result<(), ApiError> {
    let timestamp = timestamp.parse::<i64>().map_err(|_| {
        ApiError::forbidden_reason(
            "REQUEST_SIGNATURE_TIMESTAMP_INVALID",
            "X-Lumen-Timestamp must be a Unix seconds value.",
        )
    })?;
    let now = now_millis() / 1_000;
    if (now - timestamp).abs() > allowed_skew_seconds as i64 {
        return Err(ApiError::forbidden_reason(
            "REQUEST_SIGNATURE_TIMESTAMP_OUT_OF_WINDOW",
            "Request signing timestamp is outside the accepted clock-skew window.",
        ));
    }
    Ok(())
}

fn canonical_payload(
    method: &str,
    path: &str,
    query: &str,
    body: &[u8],
    timestamp: &str,
    nonce: &str,
) -> String {
    let body_hash = hex::encode(Sha256::digest(body));
    let mut values = [
        ("bodySha256", body_hash),
        ("method", method.to_uppercase()),
        ("nonce", nonce.to_owned()),
        ("path", path.to_owned()),
        ("query", query.to_owned()),
        ("timestamp", timestamp.to_owned()),
    ];
    values.sort_by(|left, right| left.0.cmp(right.0));
    values
        .into_iter()
        .map(|(key, value)| format!("{key}={value}"))
        .collect::<Vec<_>>()
        .join("\n")
}

fn validate_signature(secret: &str, canonical: &str, signature: &str) -> Result<(), ApiError> {
    if secret.trim().is_empty() {
        return Err(ApiError::forbidden_reason(
            "REQUEST_SIGNING_SECRET_MISSING",
            "Backend request signing secret is not configured.",
        ));
    }
    let mut mac = HmacSha256::new_from_slice(secret.as_bytes()).map_err(|_| ApiError::Internal)?;
    mac.update(canonical.as_bytes());
    let expected = hex::encode(mac.finalize().into_bytes());
    if expected.as_bytes().ct_eq(signature.as_bytes()).unwrap_u8() != 1 {
        return Err(ApiError::forbidden_reason(
            "REQUEST_SIGNATURE_INVALID",
            "Request signature did not match the backend signing secret and canonical payload.",
        ));
    }
    Ok(())
}

fn now_millis() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_millis() as i64)
        .unwrap_or_default()
}
