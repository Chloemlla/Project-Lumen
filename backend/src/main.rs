use project_lumen_api::{config::Config, server};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

const DEFAULT_LOG_FILTER: &str = "info,project_lumen_api=debug,tower_http=info";

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let (log_filter, filter_warning) = init_tracing();
    if let Some(warning) = filter_warning {
        tracing::warn!(%warning, fallback_filter = DEFAULT_LOG_FILTER, "invalid RUST_LOG filter");
    }
    tracing::info!(
        service = env!("CARGO_PKG_NAME"),
        version = env!("CARGO_PKG_VERSION"),
        log_filter = %log_filter,
        "backend logging initialized"
    );

    let config = Config::from_env();
    if let Err(error) = server::run(config).await {
        tracing::error!(%error, "backend startup or runtime failed");
        return Err(error);
    }

    Ok(())
}

fn init_tracing() -> (String, Option<String>) {
    let requested_filter =
        std::env::var("RUST_LOG").unwrap_or_else(|_| DEFAULT_LOG_FILTER.to_owned());
    let mut effective_filter = requested_filter.clone();
    let mut filter_warning = None;
    let env_filter = EnvFilter::try_new(&requested_filter).unwrap_or_else(|error| {
        effective_filter = DEFAULT_LOG_FILTER.to_owned();
        filter_warning = Some(format!("{error}; configured RUST_LOG={requested_filter}"));
        EnvFilter::new(DEFAULT_LOG_FILTER)
    });

    tracing_subscriber::registry()
        .with(env_filter)
        .with(
            tracing_subscriber::fmt::layer()
                .with_target(true)
                .with_file(true)
                .with_line_number(true)
                .with_thread_ids(true),
        )
        .init();

    (effective_filter, filter_warning)
}
