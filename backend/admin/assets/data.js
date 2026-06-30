window.LumenAdminData = {
  profile: {
    email: "ops-review@example.com",
    registeredAt: "2026-06-19 09:42 UTC",
    lastSyncAt: "2026-06-30 07:18 UTC",
    planTier: "PRO",
    featureFlags: ["pro_templates", "advanced_export", "cloud_sync_beta"],
    localSecurity: "HTTP cleartext allowed only for eye.chloemlla.com",
  },
  devices: [
    { id: "pixel-8-pro", model: "Pixel 8 Pro", versionCode: 1462, lastSeen: "4 min ago", config: "strict overlay on" },
    { id: "xiaomi-13", model: "Xiaomi 13", versionCode: 1451, lastSeen: "2 h ago", config: "battery optimized" },
    { id: "samsung-s23", model: "Samsung S23", versionCode: 1438, lastSeen: "3 d ago", config: "exact alarm denied" },
  ],
  accessAudit: [
    { time: "07:18", endpoint: "/api/v1/sync/push", ip: "203.0.113.42", geo: "Shanghai", status: 200 },
    { time: "07:17", endpoint: "/api/v1/entitlements", ip: "203.0.113.42", geo: "Shanghai", status: 200 },
    { time: "06:54", endpoint: "/api/v1/backups", ip: "198.51.100.21", geo: "Tokyo", status: 401 },
  ],
  purchaseAudit: [
    { time: "2026-06-29 14:22", product: "lumen_pro_lifetime", status: "pending", token: "tok_...8ec", action: "manual review" },
    { time: "2026-06-20 03:14", product: "lumen_plus_yearly", status: "revoked", token: "tok_...1ab", action: "refund sync" },
  ],
  backups: [
    {
      id: "backup_0821",
      uploadedAt: "2026-06-30 07:11",
      summary: { templates: 13, eyeStatDays: 29, pomodoroDays: 18, reminderPlans: 4, entitlements: 1 },
    },
    {
      id: "backup_0753",
      uploadedAt: "2026-06-28 22:04",
      summary: { templates: 12, eyeStatDays: 27, pomodoroDays: 17, reminderPlans: 3, entitlements: 1 },
    },
  ],
  crashes: [
    { group: "IllegalStateException @ OverlayService:148", version: "1462", count: 18, affected: 11, risk: "watch" },
    { group: "SecurityException @ AlarmReceiver:42", version: "1451", count: 7, affected: 5, risk: "risk" },
    { group: "IOException @ UpdateInstaller:117", version: "1438", count: 4, affected: 4, risk: "ok" },
  ],
  stack: [
    "java.lang.SecurityException: Settings write denied",
    "  at com.projectlumen.app.core.light.LightMonitorService.applyBrightness(LightMonitorService.kt:91)",
    "  at com.projectlumen.app.core.overlay.EyeProtectionOverlayService.show(EyeProtectionOverlayService.kt:148)",
    "  at android.app.ActivityThread.handleServiceArgs(ActivityThread.java:4811)",
    "  path=<redacted>/Project-Lumen/cache/report.json",
    "  uri=content://<redacted>",
  ],
  apiSeries: [128, 92, 110, 144, 186, 132, 118, 156, 121, 98, 88, 104],
  syncSeries: [31, 42, 38, 77, 122, 96, 84, 72, 69, 88, 110, 93],
  templates: [
    { name: "Clear sky", tier: "PRO", style: "circle", color: "#2563EB", locale: "en, zh" },
    { name: "Reading green", tier: "PRO", style: "bar", color: "#547325", locale: "en, zh" },
    { name: "Calm teal", tier: "FREE", style: "number", color: "#126B66", locale: "en, zh" },
  ],
  telemetry: [
    { label: "Daily rest goal", value: 72 },
    { label: "Pomodoro goal", value: 58 },
    { label: "Skip rate improved", value: 31 },
    { label: "Low-light rule adopted", value: 24 },
  ],
  releases: [
    { version: "1462", name: "1.2.0-a8c20e1b", sha: "f7b2...91ab", rollout: "45%", force: false },
    { version: "1451", name: "1.1.9-c43af5d0", sha: "b19c...72fa", rollout: "100%", force: false },
    { version: "1438", name: "1.1.8-d931f742", sha: "missing", rollout: "blocked", force: true },
  ],
  routes: [
    { module: "routes/session.rs", path: "/api/v1/auth/email/*", status: "ok", p95: "44 ms" },
    { module: "routes/sync.rs", path: "/api/v1/sync/*", status: "watch", p95: "189 ms" },
    { module: "routes/backups.rs", path: "/api/v1/backups/*", status: "ok", p95: "96 ms" },
    { module: "routes/purchases.rs", path: "/api/v1/purchases/google/verify", status: "watch", p95: "71 ms" },
  ],
  allowlist: [
    { origin: "eye.chloemlla.com", protocol: "http", risk: "temporary" },
    { origin: "admin.eye.chloemlla.com", protocol: "https", risk: "required" },
    { origin: "localhost", protocol: "http", risk: "local only" },
  ],
};

window.LumenAdminSections = [
  {
    id: "users",
    title: "Users & Entitlements",
    subtitle: "User assets, plan changes, feature flags, purchases, and backups.",
    modules: [
      { title: "User 360 Profile", kicker: "Identity", status: "ok", kind: "profile" },
      { title: "Device Asset Tree", kicker: "Devices", status: "watch", kind: "devices" },
      { title: "Network Access Audit", kicker: "Requests", status: "ok", kind: "accessAudit" },
      { title: "Entitlement & Plan Manager", kicker: "Billing", status: "watch", kind: "plan" },
      { title: "Cloud Backups Dashboard", kicker: "Backups", status: "ok", kind: "backups" },
    ],
  },
  {
    id: "observability",
    title: "Crash Reports & Observability",
    subtitle: "Crash grouping, sanitized stacks, API health, and sync performance.",
    modules: [
      { title: "Crash Aggregator", kicker: "Crash groups", status: "watch", kind: "crashes" },
      { title: "Sanitized Stack Review", kicker: "Clean stack", status: "ok", kind: "stack" },
      { title: "Version & Device Analysis", kicker: "Impact", status: "risk", kind: "versionAnalysis" },
      { title: "API Performance & Health", kicker: "Backend", status: "ok", kind: "apiHealth" },
      { title: "Sync Throughput Monitor", kicker: "Payloads", status: "watch", kind: "syncThroughput" },
    ],
  },
  {
    id: "content",
    title: "Core Content & Telemetry",
    subtitle: "Template CMS, parameter editing, rollout, and anonymous macro analysis.",
    modules: [
      { title: "Template CMS", kicker: "Templates", status: "ok", kind: "templateCms" },
      { title: "Visual Template Editor", kicker: "Layout", status: "ok", kind: "templateEditor" },
      { title: "Audio & Haptics Matrix", kicker: "Feedback", status: "watch", kind: "audioMatrix" },
      { title: "I18n Batch Dispatch", kicker: "Localization", status: "ok", kind: "i18n" },
      { title: "Macro Telemetry", kicker: "Anonymous", status: "info", kind: "telemetry" },
    ],
  },
  {
    id: "release",
    title: "Release & Security Ops",
    subtitle: "Integrity registry, gray rollout, route health, and HTTP allowlist review.",
    modules: [
      { title: "OTA & Integrity Registry", kicker: "Release", status: "risk", kind: "ota" },
      { title: "Forced Update & Rollout", kicker: "Policy", status: "watch", kind: "rollout" },
      { title: "Rust Routing Status", kicker: "Service topology", status: "ok", kind: "routes" },
      { title: "HTTP Allowlist Audit", kicker: "Transport", status: "risk", kind: "allowlist" },
      { title: "Admin Session Security", kicker: "Access", status: "watch", kind: "sessionSecurity" },
    ],
  },
];
