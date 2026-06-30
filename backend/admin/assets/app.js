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
  token: localStorage.getItem("projectLumenAdminToken") || "",
  tokenExpiresAt: Number(localStorage.getItem("projectLumenAdminTokenExpiresAt") || 0),
};

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
    saveToken();
  } else {
    copyText(payload || `${action} queued in ${state.environment}.`);
  }
}

function actionPayload(action) {
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
