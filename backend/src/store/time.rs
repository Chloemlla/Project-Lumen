use std::time::{SystemTime, UNIX_EPOCH};

pub(crate) fn ttl_seconds_to_millis(seconds: u64) -> i64 {
    seconds.saturating_mul(1_000).min(i64::MAX as u64) as i64
}

pub(crate) fn now_millis() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_millis().min(i64::MAX as u128) as i64)
        .unwrap_or_default()
}
