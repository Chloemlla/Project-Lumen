use crate::state::AppState;
use axum::{
    body::Body,
    extract::State,
    http::{header::AUTHORIZATION, HeaderMap, Request},
    middleware::Next,
    response::Response,
};

pub async fn audit_request(
    State(state): State<AppState>,
    request: Request<Body>,
    next: Next,
) -> Response {
    let endpoint = request.uri().path().to_owned();
    let headers = request.headers().clone();
    let ip = request_ip(&headers);
    let user_id = user_id_from_headers(&headers, &state).await.unwrap_or_default();
    let response = next.run(request).await;
    let status = response.status().as_u16();

    if let Err(error) = state
        .store
        .record_access_audit(user_id, endpoint, ip, status)
        .await
    {
        tracing::warn!(%error, "failed to record admin access audit");
    }

    response
}

async fn user_id_from_headers(headers: &HeaderMap, state: &AppState) -> Option<String> {
    let token = headers
        .get(AUTHORIZATION)
        .and_then(|header| header.to_str().ok())
        .and_then(|value| value.strip_prefix("Bearer "))
        .map(str::trim)?;
    state.store.user_for_token(token).await.ok().map(|user| user.id)
}

fn request_ip(headers: &HeaderMap) -> String {
    headers
        .get("x-forwarded-for")
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.split(',').next())
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .or_else(|| headers.get("x-real-ip").and_then(|value| value.to_str().ok()))
        .unwrap_or("unknown")
        .to_owned()
}
