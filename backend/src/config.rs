use std::env;

#[derive(Clone, Debug)]
pub struct Config {
    pub bind_address: String,
    pub api_prefix: String,
    pub admin_static_dir: String,
    pub mongodb_uri: String,
    pub mongodb_database: String,
    pub admin_username: String,
    pub admin_password: String,
    pub admin_access_token_ttl_seconds: u64,
    pub admin_refresh_token_ttl_seconds: u64,
    pub login_code: String,
    pub login_ttl_seconds: u64,
    pub access_token_ttl_seconds: u64,
    pub accept_unverified_purchases: bool,
}

impl Config {
    pub fn from_env() -> Self {
        Self {
            bind_address: env_value("LUMEN_BIND_ADDRESS", "0.0.0.0:8080"),
            api_prefix: normalize_prefix(&env_value("LUMEN_API_PREFIX", "/api")),
            admin_static_dir: env_value("LUMEN_ADMIN_STATIC_DIR", "backend/admin"),
            mongodb_uri: env_value("LUMEN_MONGODB_URI", "mongodb://localhost:27017"),
            mongodb_database: env_value("LUMEN_MONGODB_DATABASE", "project_lumen"),
            admin_username: env_value("LUMEN_ADMIN_USERNAME", "admin"),
            admin_password: env_value("LUMEN_ADMIN_PASSWORD", "change-me"),
            admin_access_token_ttl_seconds: env_u64("LUMEN_ADMIN_ACCESS_TOKEN_TTL_SECONDS", 3_600),
            admin_refresh_token_ttl_seconds: env_u64(
                "LUMEN_ADMIN_REFRESH_TOKEN_TTL_SECONDS",
                604_800,
            ),
            login_code: env_value("LUMEN_DEV_LOGIN_CODE", "000000"),
            login_ttl_seconds: env_u64("LUMEN_LOGIN_TTL_SECONDS", 600),
            access_token_ttl_seconds: env_u64("LUMEN_ACCESS_TOKEN_TTL_SECONDS", 604_800),
            accept_unverified_purchases: env_bool("LUMEN_ACCEPT_UNVERIFIED_PURCHASES", false),
        }
    }
}

fn env_value(key: &str, default: &str) -> String {
    env::var(key)
        .ok()
        .filter(|value| !value.trim().is_empty())
        .unwrap_or_else(|| default.to_owned())
}

fn env_u64(key: &str, default: u64) -> u64 {
    env::var(key)
        .ok()
        .and_then(|value| value.parse::<u64>().ok())
        .unwrap_or(default)
}

fn env_bool(key: &str, default: bool) -> bool {
    env::var(key)
        .ok()
        .map(|value| matches!(value.as_str(), "1" | "true" | "TRUE" | "yes" | "YES"))
        .unwrap_or(default)
}

fn normalize_prefix(value: &str) -> String {
    let trimmed = value.trim().trim_end_matches('/');
    if trimmed.is_empty() {
        "/api".to_owned()
    } else if trimmed.starts_with('/') {
        trimmed.to_owned()
    } else {
        format!("/{trimmed}")
    }
}
