use crate::{config::Config, store::AppStore};
use std::sync::Arc;

#[derive(Clone)]
pub struct AppState {
    pub config: Arc<Config>,
    pub store: Arc<AppStore>,
}

impl AppState {
    pub async fn connect(config: Config) -> Result<Self, crate::error::ApiError> {
        let store = AppStore::connect(&config).await?;
        Ok(Self {
            config: Arc::new(config),
            store: Arc::new(store),
        })
    }
}
