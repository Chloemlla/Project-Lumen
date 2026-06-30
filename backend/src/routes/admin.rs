use crate::{
    admin_context::require_admin,
    error::ApiError,
    models::{
        AdminActionRequest, AdminActionResponse, AdminDashboardResponse, AdminLoginRequest,
        AdminOperatorDto, AdminRefreshRequest, AdminSessionResponse,
    },
    state::AppState,
};
use axum::{
    extract::State,
    http::HeaderMap,
    routing::{get, post},
    Json, Router,
};

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/auth/login", post(login))
        .route("/auth/refresh", post(refresh))
        .route("/me", get(me))
        .route("/dashboard", get(dashboard))
        .route("/actions", post(record_action))
}

async fn login(
    State(state): State<AppState>,
    Json(payload): Json<AdminLoginRequest>,
) -> Result<Json<AdminSessionResponse>, ApiError> {
    let response = state
        .store
        .create_admin_session(&payload.username, &payload.password, &state.config)
        .await?;
    Ok(Json(response))
}

async fn refresh(
    State(state): State<AppState>,
    Json(payload): Json<AdminRefreshRequest>,
) -> Result<Json<AdminSessionResponse>, ApiError> {
    let response = state
        .store
        .refresh_admin_session(&payload.refresh_token, &state.config)
        .await?;
    Ok(Json(response))
}

async fn me(
    State(state): State<AppState>,
    headers: HeaderMap,
) -> Result<Json<AdminOperatorDto>, ApiError> {
    Ok(Json(require_admin(&headers, &state).await?))
}

async fn dashboard(
    State(state): State<AppState>,
    headers: HeaderMap,
) -> Result<Json<AdminDashboardResponse>, ApiError> {
    require_admin(&headers, &state).await?;
    Ok(Json(state.store.admin_dashboard_snapshot().await?))
}

async fn record_action(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(payload): Json<AdminActionRequest>,
) -> Result<Json<AdminActionResponse>, ApiError> {
    let operator = require_admin(&headers, &state).await?;
    Ok(Json(
        state
            .store
            .record_admin_action(&operator, payload.action, payload.payload)
            .await?,
    ))
}
