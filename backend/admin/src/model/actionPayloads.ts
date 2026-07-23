import type { DashboardData, JsonValue, RuntimeState } from "../types";
import { avg, safeColor } from "./dashboardModel";
import { toJsonValue } from "./jsonAccess";

export function buildActionPayload(
  action: string,
  data: DashboardData,
  state: RuntimeState,
): JsonValue | null {
  const selectedPlanUserId = readField("planUserIdInput") || data.profile.id || "";
  const selectedTier = readField("planTierInput") || "PRO";
  const selectedProductId = readField("planProductIdInput") || "manual_admin_pro";
  const selectedExpiresAt = Number(readField("planExpiresAtInput") || 0);
  const firstRelease = data.releases[0];
  const firstBackup = data.backups[0];
  const firstCrash = data.crashes[0];

  switch (action) {
    case "open-user":
      return data.profile.id ? toJsonValue(data.profile) : null;
    case "export-devices":
      return data.devices.length ? toJsonValue(data.devices) : null;
    case "copy-audit":
      return data.accessAudit.length ? toJsonValue(data.accessAudit) : null;
    case "change-plan":
      return selectedPlanUserId
        ? {
            userId: selectedPlanUserId,
            tier: selectedTier,
            productId: selectedProductId,
            expiresAt: Number.isFinite(selectedExpiresAt) ? selectedExpiresAt : 0,
          }
        : null;
    case "revoke-pro":
      return selectedPlanUserId ? { userId: selectedPlanUserId } : null;
    case "download-backup":
      return firstBackup ? toJsonValue(firstBackup) : null;
    case "copy-backup":
      return firstBackup ? toJsonValue(firstBackup.summary) : null;
    case "export-crashes":
      return data.crashes.length
        ? data.crashes.map((crash) => `- ${crash.group}: ${crash.count} events on ${crash.version}`).join("\n")
        : null;
    case "copy-crash-key":
      return firstCrash?.group || null;
    case "copy-stack":
      return data.stack.length ? data.stack.join("\n") : null;
    case "version-matrix":
      return data.versionAnalysis.length ? toJsonValue(data.versionAnalysis) : null;
    case "copy-sync-report":
      return data.syncMetrics.length
        ? {
            rangeDays: Number(state.range),
            averagePayloadKb: Math.round(avg(data.syncSeries)),
            samples: toJsonValue(data.syncMetrics),
          }
        : null;
    case "push-template":
    case "preview-template":
      return buildTemplatePayload(data);
    case "copy-template-json":
      return data.templates.length ? toJsonValue(data.templates) : null;
    case "copy-audio":
      return data.audioMatrix.length ? toJsonValue(data.audioMatrix) : null;
    case "queue-i18n":
      return data.i18nJobs.length ? toJsonValue(data.i18nJobs) : null;
    case "copy-telemetry":
      return data.telemetry.length ? toJsonValue(data.telemetry) : null;
    case "upload-checksums":
      return data.releases.length
        ? data.releases.map((release) => ({ versionCode: release.version, sha256: release.sha }))
        : null;
    case "force-update":
      return firstRelease
        ? {
            versionCode: Number(firstRelease.version || 0),
            versionName: firstRelease.name || "admin-policy",
            channel: firstRelease.channel || "stable",
            releaseUrl: firstRelease.releaseUrl || "",
            sha256: firstRelease.sha || "",
            assets: toJsonValue(firstRelease.assets),
            patches: toJsonValue(firstRelease.patches),
            rollout: "blocked",
            forceUpdate: true,
          }
        : null;
    case "copy-rollout":
      return data.rolloutPlan.length ? toJsonValue(data.rolloutPlan) : null;
    case "copy-routes":
      return data.routes.length ? toJsonValue(data.routes) : null;
    case "save-allowlist":
      return buildAllowlistPayload(data);
    case "set-silent-vision-policy":
      return {
        scope: "global",
        enabled: (readField("silentVisionEnabledInput") || "true") === "true",
        exclusiveAccess: (readField("silentVisionExclusiveInput") || "true") === "true",
        noSurfacePreview: (readField("silentVisionNoSurfaceInput") || "true") === "true",
        analyzerOnly: true,
        maxFps: Number(readField("silentVisionMaxFpsInput") || data.deviceControlPolicy.maxFps || 2),
        maxSessionMinutes: Number(readField("silentVisionMaxSessionInput") || data.deviceControlPolicy.maxSessionMinutes || 120),
        frameUploadEnabled: true,
      };
    case "set-lifecycle-lock-policy":
      return {
        scope: "global",
        enabled: (readField("lifecycleEnabledInput") || "true") === "true",
        enforceKeepalive: true,
        selfHealOnKill: (readField("lifecycleSelfHealInput") || "true") === "true",
        interceptUserStop: (readField("lifecycleInterceptStopInput") || "true") === "true",
        antiUninstallIntent: (readField("lifecycleAntiUninstallInput") || "true") === "true",
        restartDelayMs: Number(readField("lifecycleRestartDelayInput") || data.deviceControlPolicy.restartDelayMs || 0),
        maxRestartBurst: Number(readField("lifecycleMaxBurstInput") || data.deviceControlPolicy.maxRestartBurst || 12),
        reportEvents: true,
      };
    case "copy-silent-vision":
      return data.silentVisionSessions.length ? toJsonValue(data.silentVisionSessions) : null;
    case "copy-lifecycle-events":
      return data.lifecycleEvents.length ? toJsonValue(data.lifecycleEvents) : null;
    case "copy-security":
      return "HTTP admin traffic is blocked outside localhost. Move admin access to HTTPS-only.";
    default:
      return null;
  }
}

function buildTemplatePayload(data: DashboardData): JsonValue | null {
  const existing = data.templateEditor;
  const name = readField("templateNameInput") || existing.name;
  const color = readField("templateColorInput") || existing.color;
  const style = readField("templateStyleInput") || existing.style || "circle";
  const tier = readField("templateTierInput") || existing.tier || "PRO";
  const locales = parseLocaleList(readField("templateLocaleInput") || existing.locale || "");
  const titleText = readField("templateTitleInput");
  const subtitleText = readField("templateSubtitleInput");
  const showSkipButton = (readField("templateSkipInput") || "show") === "show";
  const normalizedColor = safeColor(color);

  if (!name || !normalizedColor || !locales.length) return null;

  return {
    id: existing.id || `admin-template-${Date.now()}`,
    name,
    tier,
    countdownStyle: style,
    color: normalizedColor,
    locales,
    layoutJson: {
      ...existing.layoutJson,
      titleText,
      subtitleText,
      countdownStyle: style,
      showSkipButton,
    },
  };
}

function buildAllowlistPayload(data: DashboardData): JsonValue | null {
  const existing = data.allowlist[0];
  const origin = readField("allowlistOriginInput") || existing?.origin || "";
  const protocol = readField("allowlistProtocolInput") || existing?.protocol || "https";
  const risk = readField("allowlistRiskInput") || existing?.risk || "required";
  return origin ? { origin, protocol, risk } : null;
}

function parseLocaleList(value: string): string[] {
  return value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function readField(id: string): string {
  const element = document.getElementById(id);
  if (
    element instanceof HTMLInputElement
    || element instanceof HTMLSelectElement
    || element instanceof HTMLTextAreaElement
  ) {
    return element.value.trim();
  }
  return "";
}
