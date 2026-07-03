const API_BASE = `${window.location.origin}/api`;
const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "::1"]);
const SENSITIVE_ACTIONS = new Set(["change-plan", "revoke-pro", "download-backup", "push-template", "force-update", "save-allowlist"]);

const data = window.LumenAdminData;
const sections = window.LumenAdminSections;
const modules = window.LumenAdminModules;
const state = {
    section: "users",
    query: "",
    environment: "production",
    range: "30",
    username: localStorage.getItem("projectLumenAdminUsername") || "admin",
    token: localStorage.getItem("projectLumenAdminToken") || "",
    refreshToken: localStorage.getItem("projectLumenAdminRefreshToken") || "",
    tokenExpiresAt: Number(localStorage.getItem("projectLumenAdminTokenExpiresAt") || 0),
};

document.addEventListener("DOMContentLoaded", () => {
  bindControls();
  updateStaticLabels();
    renderNav();
    render();
    refreshHealth();
    if (state.token) fetchDashboard();
    setInterval(updateTransportStatus, 30_000);
});

function bindControls() {
    byId("adminUsernameInput").value = state.username;
    byId("adminTokenInput").value = state.token;
    byId("loginButton").addEventListener("click", loginAdmin);
    byId("saveTokenButton").addEventListener("click", saveToken);
    byId("refreshSessionButton").addEventListener("click", refreshAdminSession);
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
      <span>${modules.escapeHtml(section.title)}</span>
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
  const visibleModules = section.modules.filter((module) => matchesQuery(module));
  const grid = byId("moduleGrid");
  grid.innerHTML = "";

  if (visibleModules.length === 0) {
    grid.innerHTML = `<div class="empty-state">No modules match the current filter.</div>`;
    return;
  }

  visibleModules.forEach((module) => grid.appendChild(renderModule(module)));
  drawPendingCharts();
  updateTransportStatus();
}

function matchesQuery(module) {
  if (!state.query) return true;
  return `${module.title} ${module.kicker} ${module.kind}`.toLowerCase().includes(state.query);
}

function renderModule(module) {
  const template = byId("moduleTemplate").content.cloneNode(true);
  const card = template.querySelector(".module-card");
  card.dataset.kind = module.kind;
  template.querySelector(".module-kicker").textContent = module.kicker;
  template.querySelector("h2").textContent = module.title;
  const status = template.querySelector(".status-pill");
  status.textContent = modules.statusLabel(module.status);
  status.className = `status-pill status-${module.status}`;
  template.querySelector(".module-body").innerHTML = modules.bodyForKind(module.kind, {
    state,
    isSecureAdminOrigin,
  });
  template.querySelector(".module-actions").innerHTML = modules.actionsForKind(module.kind);
  attachModuleActions(template);
  return card;
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
    const payload = actionPayload(action);
    if (action === "probe-health") {
        refreshHealth();
    } else if (action === "refresh-token") {
        refreshAdminSession();
    } else if (SENSITIVE_ACTIONS.has(action)) {
        if (!payload) {
            toast("Live dashboard data is required for this admin action.");
            return;
        }
        recordAdminAction(action, payload);
    } else {
        copyText(payload || `${action} queued in ${state.environment}.`);
    }
}

function actionPayload(action) {
  const selectedPlanUserId = fieldValue("planUserIdInput") || data.profile?.id || "";
  const selectedTier = fieldValue("planTierInput") || "PRO";
  const selectedProductId = fieldValue("planProductIdInput") || "manual_admin_pro";
  const selectedExpiresAt = Number(fieldValue("planExpiresAtInput") || 0);
  const firstRelease = data.releases?.[0];
  const firstReleaseCode = Number(firstRelease?.version || 0);
  const payloads = {
    "change-plan": selectedPlanUserId ? {
      userId: selectedPlanUserId,
      tier: selectedTier,
      productId: selectedProductId,
      expiresAt: Number.isFinite(selectedExpiresAt) ? selectedExpiresAt : 0,
    } : null,
    "revoke-pro": selectedPlanUserId ? { userId: selectedPlanUserId } : null,
    "push-template": {
      id: `admin-template-${Date.now()}`,
      name: "Admin dispatched template",
      tier: "PRO",
      countdownStyle: "circle",
      color: "#2563EB",
      locales: ["en", "zh"],
      layoutJson: { countdownStyle: "circle", showSkipButton: true },
    },
    "force-update": firstRelease ? {
      versionCode: firstReleaseCode,
      versionName: firstRelease.name || "admin-policy",
      sha256: firstRelease.sha || "",
      rollout: "blocked",
      forceUpdate: true,
    } : null,
    "save-allowlist": { origin: "admin.eye.chloemlla.com", protocol: "https", risk: "required" },
    "copy-stack": data.stack.join("\n"),
    "copy-audit": JSON.stringify(data.accessAudit, null, 2),
    "copy-backup": JSON.stringify(data.backups?.[0]?.summary || {}, null, 2),
    "copy-template-json": JSON.stringify(data.templates, null, 2),
    "copy-audio": "preAlert=70; restStart=75; restEnd=65; pomodoroStart=60; pomodoroEnd=80; vibration=short",
    "copy-telemetry": JSON.stringify(data.telemetry, null, 2),
    "copy-sync-report": JSON.stringify({ averagePayloadKb: 84, p95Ms: 231, rangeDays: state.range }, null, 2),
    "copy-rollout": JSON.stringify(data.releases, null, 2),
    "copy-routes": JSON.stringify(data.routes, null, 2),
    "copy-security": "HTTP admin traffic is blocked outside localhost. Move admin.eye.chloemlla.com to HTTPS-only.",
    "export-crashes": data.crashes.map((crash) => `- ${crash.group}: ${crash.count} events on ${crash.version}`).join("\n"),
  };
  return payloads[action];
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
    fetchDashboard();
}

function saveToken() {
    state.token = byId("adminTokenInput").value.trim();
    state.tokenExpiresAt = state.token ? Date.now() + 50 * 60 * 1000 : 0;
    localStorage.setItem("projectLumenAdminToken", state.token);
    localStorage.setItem("projectLumenAdminTokenExpiresAt", String(state.tokenExpiresAt));
    render();
    toast(state.token ? "Admin token saved." : "Admin token cleared.");
}

async function loginAdmin() {
    const username = byId("adminUsernameInput").value.trim();
    const password = byId("adminPasswordInput").value;
    if (!username || !password) {
        toast("Username and password are required.");
        return;
    }
    try {
        const session = await apiJson("admin/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password }),
            skipRefresh: true,
        });
        applySession(session);
        byId("adminPasswordInput").value = "";
        toast("Admin session established.");
        await fetchDashboard();
    } catch (error) {
        toast(`Login failed: ${error.message}`);
    }
}

async function refreshAdminSession() {
    if (!state.refreshToken) {
        toast("No refresh token is available.");
        return false;
    }
    try {
        const session = await apiJson("admin/auth/refresh", {
            method: "POST",
            body: JSON.stringify({ refreshToken: state.refreshToken }),
            skipRefresh: true,
        });
        applySession(session);
        toast("Admin token refreshed.");
        return true;
    } catch (error) {
        clearSession();
        toast(`Refresh failed: ${error.message}`);
        return false;
    }
}

async function fetchDashboard() {
    if (!state.token) {
        render();
        toast("Login or paste an admin token to load live data.");
        return;
    }
    try {
        const snapshot = await apiJson("admin/dashboard");
        Object.assign(data, mapDashboard(snapshot));
        updateStaticLabels();
        render();
        toast("Dashboard refreshed from MongoDB.");
    } catch (error) {
        render();
        toast(`Dashboard refresh failed: ${error.message}`);
    }
}

async function recordAdminAction(action, payload) {
    try {
        const response = await apiJson("admin/actions", {
            method: "POST",
            body: JSON.stringify({ action, payload }),
        });
        toast(response.accepted ? `${action} recorded.` : `${action} rejected.`);
        if (response.accepted) fetchDashboard();
    } catch (error) {
        toast(`Action failed: ${error.message}`);
    }
}

async function apiJson(path, options = {}) {
    const response = await fetch(`${API_BASE}/${path}`, {
        method: options.method || "GET",
        headers: {
            Accept: "application/json",
            ...(options.body ? { "Content-Type": "application/json" } : {}),
            ...authHeaders(),
        },
        body: options.body,
    });
    if (response.status === 401 && !options.skipRefresh && await refreshAdminSession()) {
        return apiJson(path, { ...options, skipRefresh: true });
    }
    const text = await response.text();
    const payload = text ? JSON.parse(text) : {};
    if (!response.ok) {
        throw new Error(payload?.error?.message || `HTTP ${response.status}`);
    }
    return payload;
}

function applySession(session) {
    state.username = session.operator?.username || state.username;
    state.token = session.accessToken;
    state.refreshToken = session.refreshToken;
    state.tokenExpiresAt = session.expiresAt || 0;
    byId("adminUsernameInput").value = state.username;
    byId("adminTokenInput").value = state.token;
    localStorage.setItem("projectLumenAdminUsername", state.username);
    localStorage.setItem("projectLumenAdminToken", state.token);
    localStorage.setItem("projectLumenAdminRefreshToken", state.refreshToken);
    localStorage.setItem("projectLumenAdminTokenExpiresAt", String(state.tokenExpiresAt));
}

function clearSession() {
    state.token = "";
    state.refreshToken = "";
    state.tokenExpiresAt = 0;
    byId("adminTokenInput").value = "";
    localStorage.removeItem("projectLumenAdminToken");
    localStorage.removeItem("projectLumenAdminRefreshToken");
    localStorage.removeItem("projectLumenAdminTokenExpiresAt");
}

function mapDashboard(snapshot) {
    const profiles = snapshot.users?.profiles || [];
    const profile = profiles[0] || {};
    const apiMetrics = snapshot.observability?.apiMetrics || [];
    const syncMetrics = snapshot.observability?.syncMetrics || [];
    const crashGroups = snapshot.observability?.crashGroups || [];
    const entitlements = snapshot.users?.entitlements || [];
    return {
        users: profiles.map((user) => ({
            id: user.id || "",
            email: user.email || "unknown",
            planTier: user.planTier || "FREE",
            lastSyncAt: formatTime(user.lastSyncAt),
        })),
        profile: {
            id: profile.id || "",
            email: profile.email || "No users yet",
            registeredAt: formatTime(profile.registeredAt),
            lastSyncAt: formatTime(profile.lastSyncAt),
            planTier: profile.planTier || "not recorded",
            featureFlags: profile.featureFlags || [],
            localSecurity: "not reported",
        },
        devices: (snapshot.users?.devices || []).map((device) => ({
            id: device.deviceInstallationId || "unknown",
            fingerprint: device.deviceFingerprint || "",
            model: device.model || "not reported",
            versionCode: device.versionCode || 0,
            lastSeen: formatTime(device.lastSeenAt),
            config: device.localSecurityConfig || "not reported",
        })),
        accessAudit: (snapshot.users?.accessAudit || []).map((entry) => ({
            time: formatTime(entry.at),
            endpoint: entry.endpoint,
            ip: entry.ip,
            geo: entry.geo,
            status: entry.status,
        })),
        purchaseAudit: (snapshot.users?.purchaseAudit || []).map((entry) => ({
            time: formatTime(entry.at),
            userId: entry.userId,
            product: entry.productId,
            status: entry.status,
            token: entry.purchaseToken,
            action: entry.action,
        })),
        entitlements: entitlements.map((entry) => ({
            userId: entry.userId,
            product: entry.productId,
            tier: entry.tier,
            status: entry.status,
            expiresAt: formatTime(entry.expiresAt),
            lastVerifiedAt: formatTime(entry.lastVerifiedAt),
        })),
        backups: (snapshot.users?.backups || []).map((backup) => ({
            id: backup.id,
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
            group: crash.groupKey,
            version: String(crash.versionCode),
            count: crash.count,
            affected: crash.affectedUsers,
            risk: crash.risk || "watch",
        })),
        stack: snapshot.observability?.cleanStack || [],
        apiMetrics,
        syncMetrics,
        apiSeries: apiMetrics.map((metric) => metric.p95Ms || 0),
        syncSeries: syncMetrics.map((metric) => metric.averagePayloadKb || 0),
        versionAnalysis: (snapshot.observability?.versionImpacts || []).map((item) => ({
            version: String(item.versionCode || 0),
            manufacturer: item.manufacturer || "unknown",
            crashes: item.crashCount || 0,
            affected: item.affectedUsers || 0,
            trend: item.trend || "clear",
            risk: item.risk || "ok",
        })),
        templates: (snapshot.content?.templates || []).map((template) => ({
            name: template.name,
            tier: template.tier,
            style: template.countdownStyle,
            color: template.color,
            locale: (template.locales || []).join(", "),
            layoutJson: template.layoutJson,
        })),
        templateEditor: (() => {
            const template = (snapshot.content?.templates || [])[0] || {};
            return {
                name: template.name || "",
                style: template.countdownStyle || "circle",
                layoutJson: template.layoutJson || {},
            };
        })(),
        audioMatrix: (snapshot.content?.audioMatrix || []).map((item) => ({
            label: item.label,
            value: item.label === "Vibration"
                ? (item.enabled ? "on" : "off")
                : `${item.enabled ? item.volumePercent : 0}%`,
            meta: `${item.enabled ? "enabled" : "disabled"} | ${item.meta || ""} | ${formatTime(item.sampledAt)}`,
        })),
        i18nJobs: (snapshot.content?.i18nJobs || []).map((item) => ({
            locale: item.locale,
            templateCount: item.templateCount || 0,
            premiumCount: item.premiumCount || 0,
            status: item.status || "ready",
            updatedAt: formatTime(item.updatedAt),
        })),
        telemetry: (snapshot.content?.telemetry || []).map((item) => ({
            label: item.label,
            value: item.value,
            rangeDays: item.rangeDays,
        })),
        releases: (snapshot.release?.releases || []).map((release) => ({
            version: String(release.versionCode),
            name: release.versionName,
            sha: release.sha256,
            rollout: release.rollout,
            force: release.forceUpdate,
        })),
        rolloutPlan: (snapshot.release?.rolloutPlan || []).map((item) => ({
            title: item.title,
            detail: item.detail,
            status: item.status || "info",
        })),
        routes: (snapshot.release?.routes || []).map((route) => ({
            module: route.module,
            path: route.path,
            status: route.state,
            p95: `${route.p95Ms || 0} ms`,
        })),
        allowlist: (snapshot.release?.allowlist || []).map((entry) => ({
            origin: entry.origin,
            protocol: entry.protocol,
            risk: entry.risk,
        })),
    };
}

function formatTime(value) {
    if (!value) return "not recorded";
    return new Date(value).toLocaleString();
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
      const color = type === "crashes" ? "#b42318" : type === "telemetry" ? "#2563eb" : "#0f766e";
      drawLineChart(canvas, series, color);
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
  for (let index = 0; index < 4; index += 1) {
    const y = padding + ((height - padding * 2) / 3) * index;
    context.beginPath();
    context.moveTo(padding, y);
    context.lineTo(width - padding, y);
    context.stroke();
  }
  context.strokeStyle = color;
  context.lineWidth = 3;
  context.beginPath();
  series.forEach((value, index) => {
    const point = chartPoint(value, index, series.length, max, width, height, padding);
    if (index === 0) context.moveTo(point.x, point.y);
    else context.lineTo(point.x, point.y);
  });
  context.stroke();
  context.fillStyle = color;
  series.forEach((value, index) => {
    const point = chartPoint(value, index, series.length, max, width, height, padding);
    context.beginPath();
    context.arc(point.x, point.y, 4, 0, Math.PI * 2);
    context.fill();
  });
}

function chartPoint(value, index, count, max, width, height, padding) {
  return {
    x: padding + ((width - padding * 2) / Math.max(count - 1, 1)) * index,
    y: height - padding - (value / max) * (height - padding * 2),
  };
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

function fieldValue(id) {
  return document.getElementById(id)?.value?.trim() || "";
}

function byId(id) {
  return document.getElementById(id);
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
