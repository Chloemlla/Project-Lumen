import {
  Activity,
  Archive,
  Clipboard,
  Code2,
  Copy,
  Download,
  Gauge,
  Globe2,
  KeyRound,
  Lock,
  Play,
  RefreshCw,
  Route,
  Save,
  ShieldCheck,
  UploadCloud,
  UserRound,
  XCircle,
} from "lucide-react";
import type { ModuleKind } from "../../types";
import type { ActionButtonConfig } from "../common";

export const moduleActions: Record<ModuleKind, ActionButtonConfig[]> = {
  profile: [{ label: "Copy user record", action: "open-user", icon: UserRound }],
  devices: [{ label: "Export device list", action: "export-devices", icon: Download }],
  accessAudit: [{ label: "Copy audit slice", action: "copy-audit", icon: Copy }],
  plan: [
    { label: "Apply plan", action: "change-plan", sensitive: true, icon: Save },
    { label: "Revoke Pro", action: "revoke-pro", sensitive: true, variant: "danger", icon: XCircle },
  ],
  backups: [
    { label: "Download JSON", action: "download-backup", sensitive: true, icon: Archive },
    { label: "Copy summary", action: "copy-backup", icon: Copy },
  ],
  crashes: [
    { label: "Export Markdown", action: "export-crashes", icon: Download },
    { label: "Copy group key", action: "copy-crash-key", icon: Copy },
  ],
  stack: [{ label: "Copy clean stack", action: "copy-stack", icon: Code2 }],
  versionAnalysis: [{ label: "Copy version matrix", action: "version-matrix", icon: Gauge }],
  apiHealth: [{ label: "Probe health", action: "probe-health", icon: RefreshCw }],
  syncThroughput: [{ label: "Copy sync report", action: "copy-sync-report", icon: Copy }],
  templateCms: [
    { label: "Push global changes", action: "push-template", sensitive: true, icon: UploadCloud },
    { label: "Copy template JSON", action: "copy-template-json", icon: Copy },
  ],
  templateEditor: [{ label: "Preview payload", action: "preview-template", icon: Play }],
  audioMatrix: [{ label: "Copy audio matrix", action: "copy-audio", icon: Clipboard }],
  i18n: [{ label: "Copy dispatch queue", action: "queue-i18n", icon: Globe2 }],
  telemetry: [{ label: "Export metrics", action: "copy-telemetry", icon: Activity }],
  ota: [
    { label: "Copy checksums", action: "upload-checksums", icon: Copy },
    { label: "Lock release", action: "force-update", sensitive: true, icon: Lock },
  ],
  rollout: [{ label: "Copy rollout plan", action: "copy-rollout", icon: Copy }],
  routes: [{ label: "Copy route status", action: "copy-routes", icon: Route }],
  allowlist: [
    { label: "Save allowlist", action: "save-allowlist", sensitive: true, icon: ShieldCheck },
    { label: "Copy warning", action: "copy-security", icon: KeyRound },
  ],
  sessionSecurity: [{ label: "Refresh token state", action: "refresh-token", icon: RefreshCw }],
};
