import { fallbackDashboardData } from "./data.js";

export const API_BASE = `${window.location.origin}/api`;
export const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "::1"]);
export const SENSITIVE_ACTIONS = new Set([
  "change-plan",
  "revoke-pro",
  "download-backup",
  "push-template",
  "force-update",
  "save-allowlist",
]);

export const LEGACY_TOKEN_STORAGE_KEYS = [
  "projectLumenAdminToken",
  "projectLumenAdminRefreshToken",
  "projectLumenAdminTokenExpiresAt",
];

export function createDashboardData() {
  return cloneValue(fallbackDashboardData);
}

export function cloneValue(value) {
  return JSON.parse(JSON.stringify(value));
}

export function clearLegacyStoredTokens() {
  LEGACY_TOKEN_STORAGE_KEYS.forEach((key) => localStorage.removeItem(key));
}

export function isSecureAdminOrigin() {
  return window.location.protocol === "https:" || LOCAL_HOSTS.has(window.location.hostname);
}

export function mapDashboard(snapshot = {}) {
  const users = snapshot.users || {};
  const observability = snapshot.observability || {};
  const content = snapshot.content || {};
  const release = snapshot.release || {};
  const profiles = users.profiles || [];
  const profile = profiles[0] || {};
  const apiMetrics = observability.apiMetrics || [];
  const syncMetrics = observability.syncMetrics || [];
  const crashGroups = observability.crashGroups || [];
  const entitlements = users.entitlements || [];
  const templates = content.templates || [];
  const firstTemplate = templates[0] || {};

  return {
    users: profiles.map((user) => ({
      id: user.id || "",
      email: user.email || "unknown",
      planTier: user.planTier || "FREE",
      lastSyncAt: formatTime(user.lastSyncAt),
    })),
    profile: {
      id: profile.id || "",
      email: profile.email || "No users loaded",
      registeredAt: formatTime(profile.registeredAt),
      lastSyncAt: formatTime(profile.lastSyncAt),
      planTier: profile.planTier || "not recorded",
      featureFlags: profile.featureFlags || [],
      localSecurity: "not reported",
    },
    devices: (users.devices || []).map((device) => ({
      id: device.deviceInstallationId || "unknown",
      fingerprint: device.deviceFingerprint || "",
      model: device.model || "not reported",
      versionCode: device.versionCode || 0,
      lastSeen: formatTime(device.lastSeenAt),
      config: device.localSecurityConfig || "not reported",
    })),
    accessAudit: (users.accessAudit || []).map((entry) => ({
      time: formatTime(entry.at),
      endpoint: entry.endpoint || "unknown",
      ip: entry.ip || "unknown",
      geo: entry.geo || "unknown",
      status: entry.status || 0,
    })),
    purchaseAudit: (users.purchaseAudit || []).map((entry) => ({
      time: formatTime(entry.at),
      userId: entry.userId || "",
      product: entry.productId || "unknown",
      status: entry.status || "unknown",
      token: entry.purchaseToken || "not recorded",
      action: entry.action || "purchase audit",
    })),
    entitlements: entitlements.map((entry) => ({
      userId: entry.userId || "",
      product: entry.productId || "unknown",
      tier: entry.tier || "FREE",
      status: entry.status || "unknown",
      expiresAt: formatTime(entry.expiresAt),
      lastVerifiedAt: formatTime(entry.lastVerifiedAt),
    })),
    backups: (users.backups || []).map((backup) => ({
      id: backup.id || "unknown",
      uploadedAt: formatTime(backup.uploadedAt),
      summary: {
        templates: backup.summary?.templates || 0,
        eyeStatDays: backup.summary?.eyeStatDays || 0,
        pomodoroDays: backup.summary?.pomodoroDays || 0,
        reminderPlans: backup.summary?.reminderPlans || 0,
        entitlements: backup.summary?.entitlements || 0,
      },
    })),
    crashes: crashGroups.map((crash) => ({
      group: crash.groupKey || "unknown",
      version: String(crash.versionCode || 0),
      count: crash.count || 0,
      affected: crash.affectedUsers || 0,
      risk: normalizeStatus(crash.risk || "watch"),
    })),
    stack: observability.cleanStack || [],
    apiMetrics,
    syncMetrics,
    apiSeries: apiMetrics.map((metric) => metric.p95Ms || 0),
    syncSeries: syncMetrics.map((metric) => metric.averagePayloadKb || 0),
    versionAnalysis: (observability.versionImpacts || []).map((item) => ({
      version: String(item.versionCode || 0),
      manufacturer: item.manufacturer || "unknown",
      crashes: item.crashCount || 0,
      affected: item.affectedUsers || 0,
      trend: item.trend || "clear",
      risk: normalizeStatus(item.risk || "ok"),
    })),
    templates: templates.map((template) => ({
      id: template.id || "",
      name: template.name || "untitled",
      tier: template.tier || "FREE",
      style: template.countdownStyle || "circle",
      color: template.color || "",
      locale: (template.locales || []).join(", "),
      layoutJson: template.layoutJson || {},
    })),
    templateEditor: {
      id: firstTemplate.id || "",
      name: firstTemplate.name || "",
      style: firstTemplate.countdownStyle || "circle",
      color: firstTemplate.color || "",
      tier: firstTemplate.tier || "PRO",
      layoutJson: firstTemplate.layoutJson || {},
    },
    audioMatrix: (content.audioMatrix || []).map((item) => ({
      label: item.label || "unknown",
      value: item.label === "Vibration"
        ? (item.enabled ? "on" : "off")
        : `${item.enabled ? item.volumePercent || 0 : 0}%`,
      meta: `${item.enabled ? "enabled" : "disabled"} | ${item.meta || ""} | ${formatTime(item.sampledAt)}`,
    })),
    i18nJobs: (content.i18nJobs || []).map((item) => ({
      locale: item.locale || "unknown",
      templateCount: item.templateCount || 0,
      premiumCount: item.premiumCount || 0,
      status: normalizeStatus(item.status || "ready"),
      updatedAt: formatTime(item.updatedAt),
    })),
    telemetry: (content.telemetry || []).map((item) => ({
      label: item.label || "unknown",
      value: item.value || 0,
      rangeDays: item.rangeDays || 7,
    })),
    releases: (release.releases || []).map((item) => ({
      version: String(item.versionCode || 0),
      name: item.versionName || "unknown",
      sha: item.sha256 || "",
      rollout: item.rollout || "not recorded",
      force: Boolean(item.forceUpdate),
    })),
    rolloutPlan: (release.rolloutPlan || []).map((item) => ({
      title: item.title || "Rollout check",
      detail: item.detail || "No detail recorded",
      status: normalizeStatus(item.status || "info"),
    })),
    routes: (release.routes || []).map((route) => ({
      module: route.module || "unknown",
      path: route.path || "/",
      status: normalizeStatus(route.state || "info"),
      p95: `${route.p95Ms || 0} ms`,
    })),
    allowlist: (release.allowlist || []).map((entry) => ({
      origin: entry.origin || "",
      protocol: entry.protocol || "https",
      risk: entry.risk || "not recorded",
    })),
  };
}

export function buildDashboardSummary(data, health, secure, session) {
  const activeEntitlements = data.entitlements.filter((entry) => entry.status === "active").length;
  const syncAverage = Math.round(avg(data.syncSeries));
  const releaseNeedsAction = data.releases.some((item) => item.force)
    || data.rolloutPlan.some((item) => normalizeStatus(item.status) === "risk");
  const sessionReady = Boolean(session.token);

  return [
    {
      id: "health",
      label: "API health",
      value: health.status || "Unknown",
      detail: health.detail || "No probe yet",
      status: health.ok ? "ok" : health.status === "Checking" ? "info" : "watch",
    },
    {
      id: "crashes",
      label: "Crash groups",
      value: String(data.crashes.length),
      detail: `${data.crashes.reduce((sum, item) => sum + item.count, 0)} total events`,
      status: data.crashes.length ? "watch" : "ok",
    },
    {
      id: "sync",
      label: "Sync throughput",
      value: `${syncAverage} KB`,
      detail: `${data.syncMetrics.length} sampled endpoints`,
      status: data.syncMetrics.length ? "info" : "watch",
    },
    {
      id: "access",
      label: "Admin access",
      value: secure ? "Secure" : "Locked",
      detail: sessionReady ? `${activeEntitlements} active grants visible` : "Login required for live data",
      status: secure ? "ok" : "risk",
    },
    {
      id: "release",
      label: "Release policy",
      value: releaseNeedsAction ? "Review" : "Clear",
      detail: `${data.releases.length} releases tracked`,
      status: releaseNeedsAction ? "risk" : "ok",
    },
  ];
}

export function moduleStatusCounts(sections) {
  return sections.reduce((accumulator, section) => {
    section.modules.forEach((module) => {
      const status = normalizeStatus(module.status);
      accumulator[status] = (accumulator[status] || 0) + 1;
    });
    return accumulator;
  }, {});
}

export function statusLabel(status) {
  return {
    ok: "OK",
    watch: "Watch",
    risk: "Risk",
    info: "Info",
    ready: "Ready",
    clear: "Clear",
    active: "Active",
  }[status] || String(status || "Info");
}

export function normalizeStatus(status) {
  const value = String(status || "info").toLowerCase();
  if (["ok", "active", "ready", "clear", "healthy", "secure", "normal"].includes(value)) return "ok";
  if (["risk", "failed", "blocked", "error", "locked", "critical"].includes(value)) return "risk";
  if (["watch", "pending", "stale", "degraded", "warning"].includes(value)) return "watch";
  return "info";
}

export function formatTime(value) {
  if (!value) return "not recorded";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "not recorded";
  return date.toLocaleString();
}

export function avg(values) {
  return values.reduce((sum, value) => sum + value, 0) / Math.max(values.length, 1);
}

export function shortFingerprint(value) {
  const normalized = String(value || "").trim();
  if (!normalized) return "not reported";
  if (normalized.length <= 24) return normalized;
  return `${normalized.slice(0, 12)}...${normalized.slice(-12)}`;
}

export function backupSummary(summary) {
  return `${summary.templates} templates, ${summary.eyeStatDays} eye stat days, ${summary.pomodoroDays} pomodoro days, ${summary.reminderPlans} plans`;
}

export function tokenExpiryLabel(expiresAt, now = Date.now()) {
  if (!expiresAt) return "Not set";
  if (expiresAt <= now) return "Expired";
  return new Date(expiresAt).toLocaleString();
}

export function safeColor(value) {
  const normalized = String(value || "").trim();
  return /^#[0-9a-f]{3,8}$/i.test(normalized) ? normalized : "";
}
