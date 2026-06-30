const API_BASE = `${window.location.origin}/api`;
const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "::1"]);
const SENSITIVE_ACTIONS = new Set([
  "change-plan",
  "revoke-pro",
  "download-backup",
  "push-template",
  "force-update",
  "save-allowlist",
]);

const state = {
  section: "users",
  query: "",
  environment: "production",
  range: "30",
  token: localStorage.getItem("projectLumenAdminToken") || "",
  tokenExpiresAt: Number(localStorage.getItem("projectLumenAdminTokenExpiresAt") || 0),
};

const data = {
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

const sections = [
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

document.addEventListener("DOMContentLoaded", () => {
  bindControls();
  updateStaticLabels();
  renderNav();
  render();
  refreshHealth();
  setInterval(updateTransportStatus, 30_000);
});

function bindControls() {
  byId("adminTokenInput").value = state.token;
  byId("saveTokenButton").addEventListener("click", saveToken);
  byId("refreshButton").addEventListener("click", refreshAll);
  byId("moduleSearch").addEventListener("input", (event) => {
    state.query = event.target.value.trim().toLowerCase();
    render();
  });
  byId("environmentSelect").addEventListener("change", (event) => {
    state.environment = event.target.value;
    render();
  });
  byId("rangeSelect").addEventListener("change", (event) => {
    state.range = event.target.value;
    render();
  });
  byId("copySecurityNoteButton").addEventListener("click", () => copyText("Admin operations require HTTPS outside localhost."));
}

function updateStaticLabels() {
  byId("apiBaseLabel").textContent = API_BASE;
  byId("crashMetric").textContent = data.crashes.length;
  byId("syncMetric").textContent = `${Math.round(avg(data.syncSeries))} KB/s`;
  byId("releaseMetric").textContent = data.releases.some((release) => release.force) ? "Action" : "Clear";
  updateTransportStatus();
}

function updateTransportStatus() {
  const secure = isSecureAdminOrigin();
  byId("securityBanner").hidden = secure;
  byId("transportStatus").textContent = secure ? "Secure admin transport" : "Sensitive actions locked";
  document.querySelectorAll("[data-sensitive='true']").forEach((button) => {
    button.disabled = !secure;
  });
}

function renderNav() {
  const nav = byId("sectionNav");
  nav.innerHTML = sections.map((section) => `
    <button class="nav-button ${section.id === state.section ? "active" : ""}" type="button" data-section="${section.id}">
      <span>${escapeHtml(section.title)}</span>
      <span class="nav-count">${section.modules.length}</span>
    </button>
  `).join("");
  nav.querySelectorAll("button").forEach((button) => {
    button.addEventListener("click", () => {
      state.section = button.dataset.section;
      renderNav();
      render();
    });
  });
}

function render() {
  const section = sections.find((item) => item.id === state.section) || sections[0];
  byId("sectionTitle").textContent = section.title;
  byId("sectionSubtitle").textContent = section.subtitle;

  const modules = section.modules.filter((module) => {
    if (!state.query) return true;
    return `${module.title} ${module.kicker} ${module.kind}`.toLowerCase().includes(state.query);
  });

  const grid = byId("moduleGrid");
  grid.innerHTML = "";
  if (modules.length === 0) {
    grid.innerHTML = `<div class="empty-state">No modules match the current filter.</div>`;
    return;
  }

  modules.forEach((module) => grid.appendChild(renderModule(module)));
  drawPendingCharts();
  updateTransportStatus();
}

function renderModule(module) {
  const template = byId("moduleTemplate").content.cloneNode(true);
  const card = template.querySelector(".module-card");
  card.dataset.kind = module.kind;
  template.querySelector(".module-kicker").textContent = module.kicker;
  template.querySelector("h2").textContent = module.title;
  const status = template.querySelector(".status-pill");
  status.textContent = statusLabel(module.status);
  status.className = `status-pill status-${module.status}`;

  const body = template.querySelector(".module-body");
  const actions = template.querySelector(".module-actions");
  body.innerHTML = bodyForKind(module.kind);
  actions.innerHTML = actionsForKind(module.kind);
  attachModuleActions(template);
  return card;
}

function bodyForKind(kind) {
  const renderers = {
    profile: renderProfile,
    devices: renderDevices,
    accessAudit: renderAccessAudit,
    plan: renderPlan,
    backups: renderBackups,
    crashes: renderCrashes,
    stack: renderStack,
    versionAnalysis: renderVersionAnalysis,
    apiHealth: renderApiHealth,
    syncThroughput: renderSyncThroughput,
    templateCms: renderTemplateCms,
    templateEditor: renderTemplateEditor,
    audioMatrix: renderAudioMatrix,
    i18n: renderI18n,
    telemetry: renderTelemetry,
    ota: renderOta,
    rollout: renderRollout,
    routes: renderRoutes,
    allowlist: renderAllowlist,
    sessionSecurity: renderSessionSecurity,
  };
  return (renderers[kind] || (() => ""))();
}

function actionsForKind(kind) {
  const actions = {
    profile: button("Open user record", "open-user"),
    devices: button("Export device list", "export-devices"),
    accessAudit: button("Copy audit slice", "copy-audit"),
    plan: `${button("Apply plan", "change-plan", true)}${button("Revoke Pro", "revoke-pro", true, "danger")}`,
    backups: `${button("Download JSON", "download-backup", true)}${button("Copy summary", "copy-backup")}`,
    crashes: `${button("Export Markdown", "export-crashes")}${button("Copy group key", "copy-crash-key")}`,
    stack: button("Copy clean stack", "copy-stack"),
    versionAnalysis: button("Open version matrix", "version-matrix"),
    apiHealth: button("Probe /api/health", "probe-health"),
    syncThroughput: button("Copy sync report", "copy-sync-report"),
    templateCms: `${button("Push global changes", "push-template", true)}${button("Copy template JSON", "copy-template-json")}`,
    templateEditor: button("Preview payload", "preview-template"),
    audioMatrix: button("Copy audio matrix", "copy-audio"),
    i18n: button("Queue dispatch", "queue-i18n"),
    telemetry: button("Export anonymous metrics", "copy-telemetry"),
    ota: `${button("Upload checksums", "upload-checksums")}${button("Lock release", "force-update", true)}`,
    rollout: button("Copy rollout plan", "copy-rollout"),
    routes: button("Copy route status", "copy-routes"),
    allowlist: `${button("Save allowlist", "save-allowlist", true)}${button("Copy warning", "copy-security")}`,
    sessionSecurity: button("Refresh token state", "refresh-token"),
  };
  return actions[kind] || "";
}

function renderProfile() {
  return `
    ${kvTable([
      ["Email", data.profile.email],
      ["Registered", data.profile.registeredAt],
      ["Last sync", data.profile.lastSyncAt],
      ["Plan tier", tag(data.profile.planTier, "ok")],
      ["Local security", data.profile.localSecurity],
    ])}
    <div class="list-stack">${data.profile.featureFlags.map((flag) => row(flag, "Feature flag enabled", tag("enabled", "ok"))).join("")}</div>
  `;
}

function renderDevices() {
  return `<div class="list-stack">${data.devices.map((device) => row(device.model, `${device.id} | versionCode ${device.versionCode} | ${device.config}`, tag(device.lastSeen, "info"))).join("")}</div>`;
}

function renderAccessAudit() {
  return table(["Time", "Endpoint", "IP", "Geo", "Status"], data.accessAudit.map((entry) => [
    entry.time,
    `<code>${escapeHtml(entry.endpoint)}</code>`,
    entry.ip,
    entry.geo,
    tag(String(entry.status), entry.status === 200 ? "ok" : "risk"),
  ]));
}

function renderPlan() {
  return `
    <div class="split-panel">
      <div>
        ${formRows([
          ["Plan tier", "select", ["FREE", "PRO", "PLUS", "TEAM", "DEVELOPER"]],
          ["Entitlement", "select", ["pro_templates", "advanced_statistics", "cloud_sync", "advanced_export"]],
          ["Expires at", "text", "0 means lifetime"],
        ])}
      </div>
      <div class="timeline">${data.purchaseAudit.map((entry) => timelineItem(entry.time, `${entry.product} | ${entry.status} | ${entry.token}`, entry.action)).join("")}</div>
    </div>
  `;
}

function renderBackups() {
  const latest = data.backups[0];
  return `
    <div class="split-panel">
      <div class="timeline">${data.backups.map((backup) => timelineItem(backup.uploadedAt, backup.id, backupSummary(backup.summary))).join("")}</div>
      <pre class="json-view">${escapeHtml(JSON.stringify(latest.summary, null, 2))}</pre>
    </div>
  `;
}

function renderCrashes() {
  return table(["Group", "Version", "Count", "Affected", "Risk"], data.crashes.map((crash) => [
    crash.group,
    crash.version,
    crash.count,
    crash.affected,
    tag(statusLabel(crash.risk), crash.risk),
  ]));
}

function renderStack() {
  return `<pre class="stack-view">${data.stack.map((line) => `<span class="stack-line ${line.includes("Exception") ? "keyword" : line.includes("redacted") ? "path" : ""}">${escapeHtml(line)}</span>`).join("\n")}</pre>`;
}

function renderVersionAnalysis() {
  return `
    <canvas class="mini-chart" data-chart="crashes" width="540" height="180"></canvas>
    ${table(["Version", "Manufacturer", "Crash rate", "Trend"], [
      ["1462", "Xiaomi", "2.8%", tag("+41%", "risk")],
      ["1462", "Samsung", "1.2%", tag("+8%", "watch")],
      ["1451", "Google", "0.4%", tag("-12%", "ok")],
    ])}
  `;
}

function renderApiHealth() {
  return `
    <canvas class="mini-chart" data-chart="api" width="540" height="180"></canvas>
    ${kvTable([
      ["p50 latency", "64 ms"],
      ["p95 latency", "189 ms"],
      ["QPS", "42"],
      ["Status mix", "99.2% 2xx / 0.7% 4xx / 0.1% 5xx"],
    ])}
  `;
}

function renderSyncThroughput() {
  return `
    <canvas class="mini-chart" data-chart="sync" width="540" height="180"></canvas>
    ${kvTable([
      ["Average payload", "84 KB"],
      ["Largest payload", "2.1 MB"],
      ["Merge duration p95", "231 ms"],
      ["Rejected payloads", "3"],
    ])}
  `;
}

function renderTemplateCms() {
  return table(["Template", "Tier", "Countdown", "Color", "Locale"], data.templates.map((template) => [
    template.name,
    tag(template.tier, template.tier === "FREE" ? "info" : "ok"),
    template.style,
    `<span class="color-swatch" style="background:${template.color}"></span> ${template.color}`,
    template.locale,
  ]));
}

function renderTemplateEditor() {
  return `
    <div class="split-panel">
      <div>${formRows([
        ["Rest title", "text", "Time to rest"],
        ["Rest subtitle", "text", "Look away from the screen"],
        ["Countdown style", "select", ["circle", "bar", "number"]],
        ["Skip button", "select", ["show", "hide"]],
      ])}</div>
      <pre class="json-view">${escapeHtml(JSON.stringify({
        countdownStyle: "circle",
        titleSizeSp: 28,
        subtitleSizeSp: 16,
        showSkipButton: true,
        safeAreaPaddingDp: 24,
      }, null, 2))}</pre>
    </div>
  `;
}

function renderAudioMatrix() {
  const cells = [
    ["Pre alert", "70%", "tone_soft"],
    ["Rest start", "75%", "tone_bell"],
    ["Rest end", "65%", "tone_clear"],
    ["Pomodoro start", "60%", "tone_focus"],
    ["Pomodoro end", "80%", "tone_done"],
    ["Vibration", "on", "short pulse"],
  ];
  return `<div class="matrix">${cells.map(([label, value, meta]) => `<div class="matrix-cell"><div class="row-title">${label}</div><div class="row-meta">${value} | ${meta}</div></div>`).join("")}</div>`;
}

function renderI18n() {
  return `
    <div class="list-stack">
      ${row("Chinese template pack", "10 Pro templates ready for dispatch", tag("ready", "ok"))}
      ${row("English fallback strings", "3 strings require review", tag("review", "watch"))}
      ${row("Global sync change", "Queued to template collection", tag("pending", "info"))}
    </div>
  `;
}

function renderTelemetry() {
  return `
    <canvas class="mini-chart" data-chart="telemetry" width="540" height="180"></canvas>
    <div class="list-stack">${data.telemetry.map((item) => row(item.label, `${item.value}% of anonymous aggregate cohort`, tag(`${item.value}%`, "info"))).join("")}</div>
  `;
}

function renderOta() {
  return table(["versionCode", "Release", "SHA256", "Rollout", "Policy"], data.releases.map((release) => [
    release.version,
    release.name,
    release.sha,
    release.rollout,
    tag(release.force ? "force update" : "normal", release.force ? "risk" : "ok"),
  ]));
}

function renderRollout() {
  return `
    <div class="list-stack">
      ${row("HTTP cleartext replacement", "Prioritize versions with HTTPS-only API transport", tag("security", "risk"))}
      ${row("Gray release channel", "45% active rollout, no hard block", tag("active", "watch"))}
      ${row("Universal APK checksum", "checksums.txt synchronized", tag("verified", "ok"))}
    </div>
  `;
}

function renderRoutes() {
  return table(["Module", "Path", "State", "p95"], data.routes.map((route) => [
    route.module,
    `<code>${escapeHtml(route.path)}</code>`,
    tag(statusLabel(route.status), route.status),
    route.p95,
  ]));
}

function renderAllowlist() {
  return table(["Origin", "Protocol", "Risk"], data.allowlist.map((entry) => [
    entry.origin,
    tag(entry.protocol, entry.protocol === "https" ? "ok" : "risk"),
    entry.risk,
  ]));
}

function renderSessionSecurity() {
  const expires = state.tokenExpiresAt > Date.now() ? new Date(state.tokenExpiresAt).toLocaleString() : "Not set";
  return `
    ${kvTable([
      ["Transport", isSecureAdminOrigin() ? tag("secure", "ok") : tag("locked", "risk")],
      ["Token stored", state.token ? tag("yes", "ok") : tag("no", "watch")],
      ["Token expiry", expires],
      ["Refresh strategy", "401-aware refresh hook; admin API refresh endpoint pending"],
    ])}
  `;
}

function attachModuleActions(scope) {
  scope.querySelectorAll("[data-action]").forEach((button) => {
    button.addEventListener("click", () => handleAction(button.dataset.action));
  });
}

function handleAction(action) {
  if (SENSITIVE_ACTIONS.has(action) && !isSecureAdminOrigin()) {
    toast("Sensitive admin action blocked until HTTPS is enabled.");
    return;
  }
  const payloads = {
    "copy-stack": data.stack.join("\n"),
    "copy-audit": JSON.stringify(data.accessAudit, null, 2),
    "copy-backup": JSON.stringify(data.backups[0].summary, null, 2),
    "copy-template-json": JSON.stringify(data.templates, null, 2),
    "copy-audio": "preAlert=70; restStart=75; restEnd=65; pomodoroStart=60; pomodoroEnd=80; vibration=short",
    "copy-telemetry": JSON.stringify(data.telemetry, null, 2),
    "copy-sync-report": JSON.stringify({ averagePayloadKb: 84, p95Ms: 231, rangeDays: state.range }, null, 2),
    "copy-rollout": JSON.stringify(data.releases, null, 2),
    "copy-routes": JSON.stringify(data.routes, null, 2),
    "copy-security": "HTTP admin traffic is blocked outside localhost. Move admin.eye.chloemlla.com to HTTPS-only.",
    "export-crashes": data.crashes.map((crash) => `- ${crash.group}: ${crash.count} events on ${crash.version}`).join("\n"),
  };
  if (action === "probe-health") {
    refreshHealth();
    return;
  }
  if (action === "refresh-token") {
    saveToken();
    return;
  }
  copyText(payloads[action] || `${action} queued in ${state.environment}.`);
}

async function refreshHealth() {
  byId("healthValue").textContent = "Checking";
  byId("healthDetail").textContent = "Probing /api/health";
  const startedAt = performance.now();
  try {
    const response = await fetch(`${API_BASE}/health`, { headers: authHeaders() });
    const duration = Math.round(performance.now() - startedAt);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const payload = await response.json();
    byId("healthValue").textContent = payload.status || "ok";
    byId("healthDetail").textContent = `${payload.service || "project-lumen-api"} ${payload.version || ""} | ${duration} ms`;
  } catch (error) {
    byId("healthValue").textContent = "Offline";
    byId("healthDetail").textContent = error.message;
  }
}

function refreshAll() {
  refreshHealth();
  render();
  toast("Dashboard refreshed.");
}

function saveToken() {
  state.token = byId("adminTokenInput").value.trim();
  state.tokenExpiresAt = state.token ? Date.now() + 50 * 60 * 1000 : 0;
  localStorage.setItem("projectLumenAdminToken", state.token);
  localStorage.setItem("projectLumenAdminTokenExpiresAt", String(state.tokenExpiresAt));
  render();
  toast(state.token ? "Admin token saved." : "Admin token cleared.");
}

function drawPendingCharts() {
  requestAnimationFrame(() => {
    document.querySelectorAll("canvas[data-chart]").forEach((canvas) => {
      const type = canvas.dataset.chart;
      const series = {
        crashes: data.crashes.map((item) => item.count),
        api: data.apiSeries,
        sync: data.syncSeries,
        telemetry: data.telemetry.map((item) => item.value),
      }[type] || [];
      drawLineChart(canvas, series, type === "crashes" ? "#b42318" : type === "telemetry" ? "#2563eb" : "#0f766e");
    });
  });
}

function drawLineChart(canvas, series, color) {
  const context = canvas.getContext("2d");
  const width = canvas.width;
  const height = canvas.height;
  const padding = 24;
  const max = Math.max(...series, 1);
  context.clearRect(0, 0, width, height);
  context.strokeStyle = "#d8e0e5";
  context.lineWidth = 1;
  for (let i = 0; i < 4; i += 1) {
    const y = padding + ((height - padding * 2) / 3) * i;
    context.beginPath();
    context.moveTo(padding, y);
    context.lineTo(width - padding, y);
    context.stroke();
  }
  context.strokeStyle = color;
  context.lineWidth = 3;
  context.beginPath();
  series.forEach((value, index) => {
    const x = padding + ((width - padding * 2) / Math.max(series.length - 1, 1)) * index;
    const y = height - padding - (value / max) * (height - padding * 2);
    if (index === 0) context.moveTo(x, y);
    else context.lineTo(x, y);
  });
  context.stroke();
  context.fillStyle = color;
  series.forEach((value, index) => {
    const x = padding + ((width - padding * 2) / Math.max(series.length - 1, 1)) * index;
    const y = height - padding - (value / max) * (height - padding * 2);
    context.beginPath();
    context.arc(x, y, 4, 0, Math.PI * 2);
    context.fill();
  });
}

function kvTable(rows) {
  return table(["Field", "Value"], rows);
}

function table(headers, rows) {
  return `
    <table class="data-table">
      <thead><tr>${headers.map((header) => `<th>${escapeHtml(header)}</th>`).join("")}</tr></thead>
      <tbody>${rows.map((rowItems) => `<tr>${rowItems.map((item) => `<td>${item}</td>`).join("")}</tr>`).join("")}</tbody>
    </table>
  `;
}

function row(title, meta, right = "") {
  return `
    <div class="list-row">
      <div><div class="row-title">${escapeHtml(title)}</div><div class="row-meta">${escapeHtml(meta)}</div></div>
      <div>${right}</div>
    </div>
  `;
}

function timelineItem(time, title, meta) {
  return `
    <div class="timeline-item">
      <div class="row-title">${escapeHtml(title)}</div>
      <div class="row-meta">${escapeHtml(time)} | ${escapeHtml(meta)}</div>
    </div>
  `;
}

function formRows(rows) {
  return rows.map(([label, type, value]) => {
    if (type === "select") {
      return `<label class="form-row"><span>${label}</span><select>${value.map((item) => `<option>${escapeHtml(item)}</option>`).join("")}</select></label>`;
    }
    return `<label class="form-row"><span>${label}</span><input type="${type}" value="${escapeHtml(value)}"></label>`;
  }).join("");
}

function tag(text, status) {
  return `<span class="tag tag-${status}">${escapeHtml(text)}</span>`;
}

function button(label, action, sensitive = false, variant = "secondary") {
  return `<button class="button ${variant}" type="button" data-action="${action}" data-sensitive="${sensitive}">${escapeHtml(label)}</button>`;
}

function backupSummary(summary) {
  return `${summary.templates} templates, ${summary.eyeStatDays} eye stat days, ${summary.pomodoroDays} pomodoro days, ${summary.reminderPlans} plans`;
}

function statusLabel(status) {
  return {
    ok: "OK",
    watch: "Watch",
    risk: "Risk",
    info: "Info",
  }[status] || status;
}

function authHeaders() {
  return state.token ? { Authorization: `Bearer ${state.token}` } : {};
}

function isSecureAdminOrigin() {
  return window.location.protocol === "https:" || LOCAL_HOSTS.has(window.location.hostname);
}

function avg(values) {
  return values.reduce((sum, value) => sum + value, 0) / Math.max(values.length, 1);
}

function byId(id) {
  return document.getElementById(id);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    toast("Copied.");
  } catch {
    toast("Clipboard unavailable.");
  }
}

function toast(message) {
  const existing = document.querySelector(".toast");
  if (existing) existing.remove();
  const node = document.createElement("div");
  node.className = "toast";
  node.textContent = message;
  Object.assign(node.style, {
    position: "fixed",
    right: "20px",
    bottom: "20px",
    zIndex: "20",
    padding: "10px 12px",
    border: "1px solid #b8c6cf",
    borderRadius: "8px",
    background: "#ffffff",
    boxShadow: "0 10px 24px rgba(32,45,55,0.12)",
    color: "#16212a",
    fontWeight: "700",
  });
  document.body.appendChild(node);
  setTimeout(() => node.remove(), 2400);
}
