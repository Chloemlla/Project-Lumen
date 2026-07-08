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
    pub admin_automation_token: String,
    pub admin_access_token_ttl_seconds: u64,
    pub admin_refresh_token_ttl_seconds: u64,
    pub login_code: String,
    pub login_ttl_seconds: u64,
    pub outemail_base_url: String,
    pub outemail_api_key: String,
    pub outemail_from: String,
    pub outemail_display_name: String,
    pub outemail_domain: String,
    pub outemail_timeout_seconds: u64,
    pub access_token_ttl_seconds: u64,
    pub refresh_token_ttl_seconds: u64,
    pub request_signing_secret: String,
    pub request_timestamp_skew_seconds: u64,
    pub require_request_signing: bool,
    pub allow_public_release_check: bool,
    pub accept_unverified_purchases: bool,
}

impl Config {
    pub fn from_env() -> Self {
        Self {
            bind_address: env_value("LUMEN_BIND_ADDRESS", "0.0.0.0:8080"),
            api_prefix: normalize_prefix(&env_value("LUMEN_API_PREFIX", "/api")),
            admin_static_dir: env_value("LUMEN_ADMIN_STATIC_DIR", "backend/admin/dist"),
            mongodb_uri: env_value("LUMEN_MONGODB_URI", "mongodb://localhost:27017"),
            mongodb_database: env_value("LUMEN_MONGODB_DATABASE", "project_lumen"),
            admin_username: env_value("LUMEN_ADMIN_USERNAME", "admin"),
            admin_password: env_value("LUMEN_ADMIN_PASSWORD", "change-me"),
            admin_automation_token: env_value("LUMEN_ADMIN_AUTOMATION_TOKEN", ""),
            admin_access_token_ttl_seconds: env_u64("LUMEN_ADMIN_ACCESS_TOKEN_TTL_SECONDS", 3_600),
            admin_refresh_token_ttl_seconds: env_u64(
                "LUMEN_ADMIN_REFRESH_TOKEN_TTL_SECONDS",
                604_800,
            ),
            login_code: env_value("LUMEN_DEV_LOGIN_CODE", "000000"),
            login_ttl_seconds: env_u64("LUMEN_LOGIN_TTL_SECONDS", 600),
            outemail_base_url: env_value("LUMEN_OUTEMAIL_BASE_URL", "https://tts.chloemlla.com"),
            outemail_api_key: env_value("LUMEN_OUTEMAIL_API_KEY", ""),
            outemail_from: env_value("LUMEN_OUTEMAIL_FROM", "noreply"),
            outemail_display_name: env_value("LUMEN_OUTEMAIL_DISPLAY_NAME", "Project Lumen"),
            outemail_domain: env_value("LUMEN_OUTEMAIL_DOMAIN", ""),
            outemail_timeout_seconds: env_u64("LUMEN_OUTEMAIL_TIMEOUT_SECONDS", 10).clamp(1, 60),
            access_token_ttl_seconds: env_u64("LUMEN_ACCESS_TOKEN_TTL_SECONDS", 7_200).min(7_200),
            refresh_token_ttl_seconds: env_u64("LUMEN_REFRESH_TOKEN_TTL_SECONDS", 2_592_000)
                .min(2_592_000),
            request_signing_secret: env_value(
                "LUMEN_REQUEST_SIGNING_SECRET",
                "project-lumen-local-request-signing-key",
            ),
            request_timestamp_skew_seconds: env_u64("LUMEN_REQUEST_TIMESTAMP_SKEW_SECONDS", 300)
                .min(300),
            require_request_signing: env_bool("LUMEN_REQUIRE_REQUEST_SIGNING", false),
            allow_public_release_check: env_bool("LUMEN_ALLOW_PUBLIC_RELEASE_CHECK", true),
            accept_unverified_purchases: env_bool("LUMEN_ACCEPT_UNVERIFIED_PURCHASES", false),
        }
    }

    pub fn redacted_mongodb_uri(&self) -> String {
        redact_connection_uri(&self.mongodb_uri)
    }

    pub fn uses_default_admin_password(&self) -> bool {
        self.admin_password == "change-me"
    }

    pub fn uses_default_login_code(&self) -> bool {
        self.login_code == "000000"
    }

    pub fn outemail_configured(&self) -> bool {
        !self.outemail_api_key.trim().is_empty() && !self.outemail_base_url.trim().is_empty()
    }

    pub fn uses_default_request_signing_secret(&self) -> bool {
        self.request_signing_secret == "project-lumen-local-request-signing-key"
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

fn redact_connection_uri(value: &str) -> String {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        return "<empty>".to_owned();
    }

    let (scheme_prefix, remainder) = trimmed
        .split_once("://")
        .map(|(scheme, rest)| (format!("{scheme}://"), rest))
        .unwrap_or_else(|| (String::new(), trimmed));
    let authority_end = remainder
        .char_indices()
        .find_map(|(index, character)| matches!(character, '/' | '?' | '#').then_some(index))
        .unwrap_or(remainder.len());
    let (authority, suffix) = remainder.split_at(authority_end);
    let redacted_authority = authority
        .rsplit_once('@')
        .map(|(_, host)| format!("***@{host}"))
        .unwrap_or_else(|| authority.to_owned());
    let suffix_without_query = suffix.split('?').next().unwrap_or(suffix);

    format!("{scheme_prefix}{redacted_authority}{suffix_without_query}")
}
