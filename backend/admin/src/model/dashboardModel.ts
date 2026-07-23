import { fallbackDashboardData } from "../data/adminSections";
import type {
  AdminSection,
  AdminSession,
  DashboardData,
  HealthState,
  JsonValue,
  StatusTone,
  SummaryCard,
} from "../types";
import {
  readArray,
  readBoolean,
  readJsonRecord,
  readNumber,
  readRecord,
  readString,
  readStringArray,
  readUnknown,
} from "./jsonAccess";

const RAW_API_BASE = import.meta.env.VITE_LUMEN_API_BASE || `${window.location.origin}/api`;

export const API_BASE = RAW_API_BASE.replace(/\/+$/, "");

export const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "::1"]);

export const SENSITIVE_ACTIONS = new Set([
  "change-plan",
  "revoke-pro",
  "download-backup",
  "push-template",
  "force-update",
  "save-allowlist",
  "set-silent-vision-policy",
  "set-lifecycle-lock-policy",
]);

export const SERVER_ADMIN_ACTIONS = new Set([
  "change-plan",
  "revoke-pro",
  "push-template",
  "force-update",
  "save-allowlist",
  "set-silent-vision-policy",
  "set-lifecycle-lock-policy",
]);

export const LEGACY_TOKEN_STORAGE_KEYS = [
  "projectLumenAdminToken",
  "projectLumenAdminRefreshToken",
  "projectLumenAdminTokenExpiresAt",
];

export function createDashboardData(): DashboardData {
  return cloneDashboardData(fallbackDashboardData);
}

export function cloneJson<T extends JsonValue>(value: T): T {
  return structuredClone(value);
}

export function cloneDashboardData(value: DashboardData): DashboardData {
  return structuredClone(value);
}

export function clearLegacyStoredTokens(): void {
  LEGACY_TOKEN_STORAGE_KEYS.forEach((key) => localStorage.removeItem(key));
}

export function isSecureAdminOrigin(): boolean {
  return window.location.protocol === "https:" || LOCAL_HOSTS.has(window.location.hostname);
}

export function mapDashboard(snapshot: unknown): DashboardData {
  const users = readRecord(snapshot, "users");
  const observability = readRecord(snapshot, "observability");
  const content = readRecord(snapshot, "content");
  const release = readRecord(snapshot, "release");
  const profiles = readArray(users, "profiles");
  const profile = profiles[0] ?? {};
  const apiMetrics = readArray(observability, "apiMetrics").map((metric) => ({
    endpoint: readString(metric, "endpoint", "unknown"),
    qps: readNumber(metric, "qps"),
    p95Ms: readNumber(metric, "p95Ms"),
    status2xx: readNumber(metric, "status2xx"),
    status4xx: readNumber(metric, "status4xx"),
    status5xx: readNumber(metric, "status5xx"),
  }));
  const syncMetrics = readArray(observability, "syncMetrics").map((metric) => ({
    endpoint: readString(metric, "endpoint", "unknown"),
    averagePayloadKb: readNumber(metric, "averagePayloadKb"),
    largestPayloadKb: readNumber(metric, "largestPayloadKb"),
    p95Ms: readNumber(metric, "p95Ms"),
    rejectedPayloads: readNumber(metric, "rejectedPayloads"),
  }));
  const templates = readArray(content, "templates");
  const firstTemplate = templates[0] ?? {};

  return {
    users: profiles.map((user) => ({
      id: readString(user, "id"),
      email: readString(user, "email", "unknown"),
      planTier: readString(user, "planTier", "FREE"),
      lastSyncAt: formatTime(readTimeValue(user, "lastSyncAt")),
    })),
    profile: {
      id: readString(profile, "id"),
      email: readString(profile, "email", "No users loaded"),
      registeredAt: formatTime(readTimeValue(profile, "registeredAt")),
      lastSyncAt: formatTime(readTimeValue(profile, "lastSyncAt")),
      planTier: readString(profile, "planTier", "not recorded"),
      featureFlags: readStringArray(profile, "featureFlags"),
      localSecurity: "not reported",
    },
    devices: readArray(users, "devices").map((device) => ({
      id: readString(device, "deviceInstallationId", "unknown"),
      fingerprint: readString(device, "deviceFingerprint"),
      model: readString(device, "model", "not reported"),
      versionCode: readNumber(device, "versionCode"),
      lastSeen: formatTime(readTimeValue(device, "lastSeenAt")),
      config: readString(device, "localSecurityConfig", "not reported"),
    })),
    accessAudit: readArray(users, "accessAudit").map((entry) => ({
      time: formatTime(readTimeValue(entry, "at")),
      endpoint: readString(entry, "endpoint", "unknown"),
      ip: readString(entry, "ip", "unknown"),
      geo: readString(entry, "geo", "unknown"),
      status: readNumber(entry, "status"),
    })),
    purchaseAudit: readArray(users, "purchaseAudit").map((entry) => ({
      time: formatTime(readTimeValue(entry, "at")),
      userId: readString(entry, "userId"),
      product: readString(entry, "productId", "unknown"),
      status: readString(entry, "status", "unknown"),
      token: readString(entry, "purchaseToken", "not recorded"),
      action: readString(entry, "action", "purchase audit"),
    })),
    entitlements: readArray(users, "entitlements").map((entry) => ({
      userId: readString(entry, "userId"),
      product: readString(entry, "productId", "unknown"),
      tier: readString(entry, "tier", "FREE"),
      status: readString(entry, "status", "unknown"),
      expiresAt: formatTime(readTimeValue(entry, "expiresAt")),
      lastVerifiedAt: formatTime(readTimeValue(entry, "lastVerifiedAt")),
    })),
    backups: readArray(users, "backups").map((backup) => {
      const summary = readRecord(backup, "summary");
      return {
        id: readString(backup, "id", "unknown"),
        uploadedAt: formatTime(readTimeValue(backup, "uploadedAt")),
        summary: {
          templates: readNumber(summary, "templates"),
          eyeStatDays: readNumber(summary, "eyeStatDays"),
          pomodoroDays: readNumber(summary, "pomodoroDays"),
          reminderPlans: readNumber(summary, "reminderPlans"),
          entitlements: readNumber(summary, "entitlements"),
        },
      };
    }),
    crashes: readArray(observability, "crashGroups").map((crash) => ({
      group: readString(crash, "groupKey", "unknown"),
      version: String(readNumber(crash, "versionCode")),
      count: readNumber(crash, "count"),
      affected: readNumber(crash, "affectedUsers"),
      risk: normalizeStatus(readString(crash, "risk", "watch")),
    })),
    stack: readArray(observability, "cleanStack")
      .map((line) => (typeof line === "string" ? line : ""))
      .filter(Boolean),
    apiMetrics,
    syncMetrics,
    apiSeries: apiMetrics.map((metric) => metric.p95Ms),
    syncSeries: syncMetrics.map((metric) => metric.averagePayloadKb),
    versionAnalysis: readArray(observability, "versionImpacts").map((item) => ({
      version: String(readNumber(item, "versionCode")),
      manufacturer: readString(item, "manufacturer", "unknown"),
      crashes: readNumber(item, "crashCount"),
      affected: readNumber(item, "affectedUsers"),
      trend: readString(item, "trend", "clear"),
      risk: normalizeStatus(readString(item, "risk", "ok")),
    })),
    templates: templates.map((template) => mapTemplate(template)),
    templateEditor: mapTemplate(firstTemplate),
    audioMatrix: readArray(content, "audioMatrix").map((item) => ({
      label: readString(item, "label", "unknown"),
      value: readString(item, "label") === "Vibration"
        ? (readBoolean(item, "enabled") ? "on" : "off")
        : `${readBoolean(item, "enabled") ? readNumber(item, "volumePercent") : 0}%`,
      meta: `${readBoolean(item, "enabled") ? "enabled" : "disabled"} | ${readString(item, "meta")} | ${formatTime(readTimeValue(item, "sampledAt"))}`,
    })),
    i18nJobs: readArray(content, "i18nJobs").map((item) => ({
      locale: readString(item, "locale", "unknown"),
      templateCount: readNumber(item, "templateCount"),
      premiumCount: readNumber(item, "premiumCount"),
      status: normalizeStatus(readString(item, "status", "ready")),
      updatedAt: formatTime(readTimeValue(item, "updatedAt")),
    })),
    telemetry: readArray(content, "telemetry").map((item) => ({
      label: readString(item, "label", "unknown"),
      value: readNumber(item, "value"),
      rangeDays: readNumber(item, "rangeDays", 7),
    })),
    releases: readArray(release, "releases").map((item) => ({
      version: String(readNumber(item, "versionCode")),
      name: readString(item, "versionName", "unknown"),
      channel: readString(item, "channel", "stable"),
      releaseUrl: readString(item, "releaseUrl"),
      sha: readString(item, "sha256"),
      assets: readArray(item, "assets").map((asset) => ({
        abi: readString(asset, "abi", "universal"),
        name: readString(asset, "name", "Project-Lumen_android_universal.apk"),
        url: readString(asset, "url"),
        sha256: readString(asset, "sha256"),
        sizeBytes: readNumber(asset, "sizeBytes"),
        contentType: readString(asset, "contentType", "application/vnd.android.package-archive"),
      })),
      patches: readArray(item, "patches").map((patch) => ({
        fromVersionCode: readNumber(patch, "fromVersionCode"),
        fromSha256: readString(patch, "fromSha256"),
        toSha256: readString(patch, "toSha256"),
        patchUrl: readString(patch, "patchUrl"),
        patchSha256: readString(patch, "patchSha256"),
        algorithm: readString(patch, "algorithm", "bsdiff"),
        sizeBytes: readNumber(patch, "sizeBytes"),
      })),
      rollout: readString(item, "rollout", "not recorded"),
      force: readBoolean(item, "forceUpdate"),
    })),
    rolloutPlan: readArray(release, "rolloutPlan").map((item) => ({
      title: readString(item, "title", "Rollout check"),
      detail: readString(item, "detail", "No detail recorded"),
      status: normalizeStatus(readString(item, "status", "info")),
    })),
    routes: readArray(release, "routes").map((route) => ({
      module: readString(route, "module", "unknown"),
      path: readString(route, "path", "/"),
      status: normalizeStatus(readString(route, "state", "info")),
      p95: `${readNumber(route, "p95Ms")} ms`,
    })),
    allowlist: readArray(release, "allowlist").map((entry) => ({
      origin: readString(entry, "origin"),
      protocol: readString(entry, "protocol", "https"),
      risk: readString(entry, "risk", "not recorded"),
    })),
    silentVisionSessions: readArray(content, "silentVisionSessions").map((row) => ({
      id: readString(row, "id"),
      userId: readString(row, "userId"),
      deviceId: readString(row, "deviceInstallationId", "unknown"),
      exclusive: readBoolean(row, "exclusiveAccess"),
      noSurface: readBoolean(row, "noSurfacePreview"),
      framesCaptured: readNumber(row, "framesCaptured"),
      framesUploaded: readNumber(row, "framesUploaded"),
      exclusiveHeld: readBoolean(row, "exclusiveHeld"),
      surfaceDetached: readBoolean(row, "surfaceDetached"),
      startedAt: formatTime(readTimeValue(row, "startedAt")),
      lastHeartbeatAt: formatTime(readTimeValue(row, "lastHeartbeatAt")),
      expiresAt: formatTime(readTimeValue(row, "expiresAt")),
      status: readString(row, "status", "unknown"),
    })),
    lifecycleEvents: readArray(content, "lifecycleEvents").map((row) => ({
      id: readString(row, "id"),
      userId: readString(row, "userId"),
      deviceId: readString(row, "deviceInstallationId", "unknown"),
      eventType: readString(row, "eventType", "unknown"),
      processName: readString(row, "processName", "unknown"),
      reason: readString(row, "reason", ""),
      selfHealed: readBoolean(row, "selfHealed"),
      restartCount: readNumber(row, "restartCount"),
      reportedAt: formatTime(readTimeValue(row, "clientReportedAt")),
      receivedAt: formatTime(readTimeValue(row, "receivedAt")),
    })),
    deviceControlPolicy: (() => {
      const policy = readRecord(content, "deviceControlPolicy");
      const silent = readRecord(policy, "silentVision");
      const life = readRecord(policy, "lifecycleLock");
      return {
        source: readString(policy, "source", "default"),
        updatedAt: formatTime(readTimeValue(policy, "updatedAt")),
        silentVisionEnabled: readBoolean(silent, "enabled", false),
        exclusiveAccess: readBoolean(silent, "exclusiveAccess", false),
        noSurfacePreview: readBoolean(silent, "noSurfacePreview", false),
        analyzerOnly: readBoolean(silent, "analyzerOnly", true),
        lifecycleEnabled: readBoolean(life, "enabled", false),
        selfHealOnKill: readBoolean(life, "selfHealOnKill", false),
        interceptUserStop: readBoolean(life, "interceptUserStop", false),
        antiUninstallIntent: readBoolean(life, "antiUninstallIntent", false),
        maxFps: readNumber(silent, "maxFps", 2),
        maxSessionMinutes: readNumber(silent, "maxSessionMinutes", 120),
        restartDelayMs: readNumber(life, "restartDelayMs"),
        maxRestartBurst: readNumber(life, "maxRestartBurst", 3),
      };
    })(),
  };
}

export function parseAdminSession(payload: unknown, fallbackUsername: string): AdminSession | null {
  const token = readString(payload, "accessToken");
  const refreshToken = readString(payload, "refreshToken");
  if (!token) return null;
  return {
    username: readString(readRecord(payload, "operator"), "username", fallbackUsername),
    token,
    refreshToken,
    tokenExpiresAt: readNumber(payload, "expiresAt"),
  };
}

export function buildDashboardSummary(
  data: DashboardData,
  health: HealthState,
  secure: boolean,
  session: AdminSession,
): SummaryCard[] {
  const activeEntitlements = data.entitlements.filter((entry) => entry.status === "active").length;
  const syncAverage = Math.round(avg(data.syncSeries));
  const releaseNeedsAction = data.releases.some((item) => item.force)
    || data.rolloutPlan.some((item) => item.status === "risk");
  const sessionReady = Boolean(session.token);

  return [
    {
      id: "health",
      label: "API health",
      value: health.status || "Unknown",
      detail: health.detail || "No probe yet",
      status: health.ok ? "ok" : health.status === "Checking" ? "info" : "watch",
      latencyMs: health.latencyMs,
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

export function moduleStatusCounts(sections: AdminSection[]): Partial<Record<StatusTone, number>> {
  return sections.reduce<Partial<Record<StatusTone, number>>>((accumulator, section) => {
    section.modules.forEach((module) => {
      accumulator[module.status] = (accumulator[module.status] || 0) + 1;
    });
    return accumulator;
  }, {});
}

export function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    ok: "OK",
    watch: "Watch",
    risk: "Risk",
    info: "Info",
    ready: "Ready",
    clear: "Clear",
    active: "Active",
  };
  return labels[status] || status || "Info";
}

export function normalizeStatus(status: string): StatusTone {
  const value = status.toLowerCase();
  if (["ok", "active", "ready", "clear", "healthy", "secure", "normal"].includes(value)) return "ok";
  if (["risk", "failed", "blocked", "error", "locked", "critical"].includes(value)) return "risk";
  if (["watch", "pending", "stale", "degraded", "warning"].includes(value)) return "watch";
  return "info";
}

export function formatTime(value: number | string | null): string {
  if (value === null || value === "" || value === 0) return "not recorded";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "not recorded";
  return date.toLocaleString();
}

export function avg(values: number[]): number {
  return values.reduce((sum, value) => sum + value, 0) / Math.max(values.length, 1);
}

export function shortFingerprint(value: string): string {
  const normalized = value.trim();
  if (!normalized) return "not reported";
  if (normalized.length <= 24) return normalized;
  return `${normalized.slice(0, 12)}...${normalized.slice(-12)}`;
}

export function backupSummary(summary: {
  templates: number;
  eyeStatDays: number;
  pomodoroDays: number;
  reminderPlans: number;
}): string {
  return `${summary.templates} templates, ${summary.eyeStatDays} eye stat days, ${summary.pomodoroDays} pomodoro days, ${summary.reminderPlans} plans`;
}

export function tokenExpiryLabel(expiresAt: number, now = Date.now()): string {
  if (!expiresAt) return "Not set";
  if (expiresAt <= now) return "Expired";
  return new Date(expiresAt).toLocaleString();
}

export function safeColor(value: string): string {
  const normalized = value.trim();
  return /^#[0-9a-f]{3,8}$/i.test(normalized) ? normalized : "";
}

function mapTemplate(template: unknown) {
  const locales = readArray(template, "locales")
    .map((item) => (typeof item === "string" ? item : ""))
    .filter(Boolean);
  return {
    id: readString(template, "id"),
    name: readString(template, "name", ""),
    tier: readString(template, "tier", "PRO"),
    style: readString(template, "countdownStyle", "circle"),
    color: readString(template, "color"),
    locale: locales.join(", "),
    layoutJson: readJsonRecord(template, "layoutJson"),
  };
}

function readTimeValue(source: unknown, key: string): number | string | null {
  const jsonValue = readUnknown(source, key);
  if (typeof jsonValue === "number" || typeof jsonValue === "string") return jsonValue;
  return null;
}
