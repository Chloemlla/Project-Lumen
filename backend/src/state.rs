use crate::{config::Config, store::AppStore};
use std::{sync::Arc, time::Instant};

#[derive(Clone)]
pub struct AppState {
    pub config: Arc<Config>,
    pub store: Arc<AppStore>,
}

impl AppState {
    pub async fn connect(config: Config) -> Result<Self, crate::error::ApiError> {
        let started_at = Instant::now();
        tracing::debug!(phase = "state.connect", "creating application store");
        let store = AppStore::connect(&config).await?;
        tracing::debug!(
            phase = "state.connect",
            elapsed_ms = elapsed_ms(&started_at),
            "application store created"
        );
        Ok(Self {
            config: Arc::new(config),
            store: Arc::new(store),
        })
    }
}

fn elapsed_ms(started_at: &Instant) -> u64 {
    started_at.elapsed().as_millis().min(u128::from(u64::MAX)) as u64
}
