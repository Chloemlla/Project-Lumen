export type StatusTone = "ok" | "watch" | "risk" | "info";

export type ModuleKind =
  | "profile"
  | "devices"
  | "accessAudit"
  | "plan"
  | "backups"
  | "crashes"
  | "stack"
  | "versionAnalysis"
  | "apiHealth"
  | "syncThroughput"
  | "templateCms"
  | "templateEditor"
  | "audioMatrix"
  | "i18n"
  | "telemetry"
  | "ota"
  | "rollout"
  | "routes"
  | "allowlist"
  | "sessionSecurity"
  | "silentVision"
  | "lifecycleLock";

export type JsonPrimitive = string | number | boolean | null;
export type JsonValue = JsonPrimitive | JsonValue[] | { [key: string]: JsonValue };
export type JsonRecord = { [key: string]: JsonValue };

export type ModuleDefinition = {
  title: string;
  kicker: string;
  status: StatusTone;
  kind: ModuleKind;
};

export type AdminSection = {
  id: string;
  title: string;
  subtitle: string;
  modules: ModuleDefinition[];
};

export type UserOption = {
  id: string;
  email: string;
  planTier: string;
  lastSyncAt: string;
};

export type ProfileSummary = {
  id: string;
  email: string;
  registeredAt: string;
  lastSyncAt: string;
  planTier: string;
  featureFlags: string[];
  localSecurity: string;
};

export type DeviceAsset = {
  id: string;
  fingerprint: string;
  model: string;
  versionCode: number;
  lastSeen: string;
  config: string;
};

export type AccessAuditEntry = {
  time: string;
  endpoint: string;
  ip: string;
  geo: string;
  status: number;
};

export type PurchaseAuditEntry = {
  time: string;
  userId: string;
  product: string;
  status: string;
  token: string;
  action: string;
};

export type EntitlementItem = {
  userId: string;
  product: string;
  tier: string;
  status: string;
  expiresAt: string;
  lastVerifiedAt: string;
};

export type BackupSummary = {
  templates: number;
  eyeStatDays: number;
  pomodoroDays: number;
  reminderPlans: number;
  entitlements: number;
};

export type BackupSnapshot = {
  id: string;
  uploadedAt: string;
  summary: BackupSummary;
};

export type CrashGroup = {
  group: string;
  version: string;
  count: number;
  affected: number;
  risk: StatusTone;
};

export type ApiMetric = {
  endpoint: string;
  qps: number;
  p95Ms: number;
  status2xx: number;
  status4xx: number;
  status5xx: number;
};

export type SyncMetric = {
  endpoint: string;
  averagePayloadKb: number;
  largestPayloadKb: number;
  p95Ms: number;
  rejectedPayloads: number;
};

export type VersionImpact = {
  version: string;
  manufacturer: string;
  crashes: number;
  affected: number;
  trend: string;
  risk: StatusTone;
};

export type TemplateItem = {
  id: string;
  name: string;
  tier: string;
  style: string;
  color: string;
  locale: string;
  layoutJson: JsonRecord;
};

export type AudioMatrixItem = {
  label: string;
  value: string;
  meta: string;
};

export type I18nJob = {
  locale: string;
  templateCount: number;
  premiumCount: number;
  status: StatusTone;
  updatedAt: string;
};

export type TelemetryItem = {
  label: string;
  value: number;
  rangeDays: number;
};

export type ReleaseItem = {
  version: string;
  name: string;
  channel: string;
  releaseUrl: string;
  sha: string;
  assets: ReleaseAssetItem[];
  patches: ReleasePatchItem[];
  rollout: string;
  force: boolean;
};

export type ReleaseAssetItem = {
  abi: string;
  name: string;
  url: string;
  sha256: string;
  sizeBytes: number;
  contentType: string;
};

export type ReleasePatchItem = {
  fromVersionCode: number;
  fromSha256: string;
  toSha256: string;
  patchUrl: string;
  patchSha256: string;
  algorithm: string;
  sizeBytes: number;
};

export type RolloutPlanItem = {
  title: string;
  detail: string;
  status: StatusTone;
};

export type RouteStatusItem = {
  module: string;
  path: string;
  status: StatusTone;
  p95: string;
};

export type SecurityAllowlistItem = {
  origin: string;
  protocol: string;
  risk: string;
};

export type SilentVisionSessionItem = {
  id: string;
  userId: string;
  deviceId: string;
  exclusive: boolean;
  noSurface: boolean;
  framesCaptured: number;
  framesUploaded: number;
  exclusiveHeld: boolean;
  surfaceDetached: boolean;
  startedAt: string;
  lastHeartbeatAt: string;
  expiresAt: string;
  status: string;
};

export type LifecycleEventItem = {
  id: string;
  userId: string;
  deviceId: string;
  eventType: string;
  processName: string;
  reason: string;
  selfHealed: boolean;
  restartCount: number;
  reportedAt: string;
  receivedAt: string;
};

export type DeviceControlPolicySummary = {
  source: string;
  updatedAt: string;
  silentVisionEnabled: boolean;
  exclusiveAccess: boolean;
  noSurfacePreview: boolean;
  analyzerOnly: boolean;
  lifecycleEnabled: boolean;
  selfHealOnKill: boolean;
  interceptUserStop: boolean;
  antiUninstallIntent: boolean;
  maxFps: number;
  maxSessionMinutes: number;
  restartDelayMs: number;
  maxRestartBurst: number;
};

export type DashboardData = {
  users: UserOption[];
  profile: ProfileSummary;
  devices: DeviceAsset[];
  accessAudit: AccessAuditEntry[];
  purchaseAudit: PurchaseAuditEntry[];
  entitlements: EntitlementItem[];
  backups: BackupSnapshot[];
  crashes: CrashGroup[];
  stack: string[];
  apiMetrics: ApiMetric[];
  syncMetrics: SyncMetric[];
  apiSeries: number[];
  syncSeries: number[];
  versionAnalysis: VersionImpact[];
  templates: TemplateItem[];
  templateEditor: TemplateItem;
  audioMatrix: AudioMatrixItem[];
  i18nJobs: I18nJob[];
  telemetry: TelemetryItem[];
  releases: ReleaseItem[];
  rolloutPlan: RolloutPlanItem[];
  routes: RouteStatusItem[];
  allowlist: SecurityAllowlistItem[];
  silentVisionSessions: SilentVisionSessionItem[];
  lifecycleEvents: LifecycleEventItem[];
  deviceControlPolicy: DeviceControlPolicySummary;
};

export type AdminSession = {
  username: string;
  token: string;
  refreshToken: string;
  tokenExpiresAt: number;
};

export type HealthState = {
  ok: boolean;
  status: string;
  detail: string;
  latencyMs: number;
  checkedAt: string;
};

export type LoadingState = {
  health: boolean;
  dashboard: boolean;
  login: boolean;
  refresh: boolean;
  action: string;
};

export type RuntimeState = {
  section: string;
  query: string;
  environment: string;
  range: string;
  token: string;
  tokenExpiresAt: number;
  now: number;
};

export type SummaryCard = {
  id: string;
  label: string;
  value: string;
  detail: string;
  status: StatusTone;
  latencyMs?: number;
};

export type ToastTone = StatusTone;

export type ToastItem = {
  id: string;
  message: string;
  tone: ToastTone;
};
