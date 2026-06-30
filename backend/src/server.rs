use crate::{api, config::Config, state::AppState};
use tokio::net::TcpListener;
use tower_http::{
    cors::{Any, CorsLayer},
    trace::TraceLayer,
};

pub async fn run(config: Config) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let bind_address = config.bind_address.clone();
    let state = AppState::connect(config).await?;
    let app = api::router(state)
        .layer(CorsLayer::new().allow_origin(Any).allow_methods(Any).allow_headers(Any))
        .layer(TraceLayer::new_for_http());

    let listener = TcpListener::bind(&bind_address).await?;
    tracing::info!(%bind_address, "Project Lumen API listening");
    axum::serve(listener, app).await?;
    Ok(())
}
