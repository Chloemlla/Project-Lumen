import {
  AlertTriangle,
  CheckCircle2,
  Gauge,
  KeyRound,
  LayoutDashboard,
  LogIn,
  RefreshCw,
  Search,
  ShieldAlert,
  ShieldCheck,
  SlidersHorizontal,
} from "lucide-react";
import { createRoot } from "react-dom/client";
import { requestJson, probeHealth } from "./admin-api.js";
import { adminSections } from "./data.js";
import {
  API_BASE,
  SENSITIVE_ACTIONS,
  avg,
  buildDashboardSummary,
  clearLegacyStoredTokens,
  cloneValue,
  createDashboardData,
  formatTime,
  isSecureAdminOrigin,
  mapDashboard,
  moduleStatusCounts,
  normalizeStatus,
  safeColor,
  statusLabel,
} from "./dashboard-model.js";
import { ModuleCard, StatusPill } from "./module-components.js";
import { React, html } from "./ui.js";

const USERNAME_STORAGE_KEY = "projectLumenAdminUsername";
const INITIAL_USERNAME = localStorage.getItem(USERNAME_STORAGE_KEY) || "admin";

function AdminDashboardApp() {
  const [dashboard, setDashboard] = React.useState(() => createDashboardData());
  const [sectionId, setSectionId] = React.useState("users");
  const [query, setQuery] = React.useState("");
  const [environment, setEnvironment] = React.useState("production");
  const [range, setRange] = React.useState("30");
  const [now, setNow] = React.useState(Date.now());
  const [lastUpdated, setLastUpdated] = React.useState("");
  const [health, setHealth] = React.useState({
    ok: false,
    status: "Unknown",
    detail: "No probe yet",
    latencyMs: 0,
    checkedAt: "",
  });
  const [authForm, setAuthForm] = React.useState({
    username: INITIAL_USERNAME,
    password: "",
    token: "",
  });
  const [session, setSession] = React.useState({
    username: INITIAL_USERNAME,
    token: "",
    refreshToken: "",
    tokenExpiresAt: 0,
  });
  const [loading, setLoading] = React.useState({
    health: false,
    dashboard: false,
    login: false,
    refresh: false,
    action: "",
  });
  const [toasts, setToasts] = React.useState([]);
  const sessionRef = React.useRef(session);
  const secure = isSecureAdminOrigin();
  const selectedSection = adminSections.find((section) => section.id === sectionId) || adminSections[0];
  const visibleModules = selectedSection.modules.filter((module) => matchesQuery(module, query));
  const summaryCards = buildDashboardSummary(dashboard, health, secure, session);
  const statusCounts = moduleStatusCounts(adminSections);
  const runtimeState = {
    section: sectionId,
    query,
    environment,
    range,
    token: session.token,
    tokenExpiresAt: session.tokenExpiresAt,
    now,
  };

  React.useEffect(() => {
    sessionRef.current = session;
  }, [session]);

  const updateLoading = React.useCallback((patch) => {
    setLoading((current) => ({ ...current, ...patch }));
  }, []);

  const notify = React.useCallback((message, tone = "info") => {
    const id = `${Date.now()}-${Math.random()}`;
    setToasts((current) => [...current.slice(-2), { id, message, tone }]);
    window.setTimeout(() => {
      setToasts((current) => current.filter((toast) => toast.id !== id));
    }, 3200);
  }, []);

  const applySession = React.useCallback((payload) => {
    const nextSession = {
      username: payload.operator?.username || sessionRef.current.username || INITIAL_USERNAME,
      token: payload.accessToken || "",
      refreshToken: payload.refreshToken || "",
      tokenExpiresAt: payload.expiresAt || 0,
    };
    sessionRef.current = nextSession;
    setSession(nextSession);
    setAuthForm((current) => ({
      ...current,
      username: nextSession.username,
      password: "",
      token: "",
    }));
    localStorage.setItem(USERNAME_STORAGE_KEY, nextSession.username);
  }, []);

  const clearSession = React.useCallback(() => {
    const nextSession = {
      username: sessionRef.current.username || INITIAL_USERNAME,
      token: "",
      refreshToken: "",
      tokenExpiresAt: 0,
    };
    sessionRef.current = nextSession;
    setSession(nextSession);
    setAuthForm((current) => ({ ...current, token: "" }));
    setDashboard(createDashboardData());
    setLastUpdated("");
  }, []);

  const refreshAdminSession = React.useCallback(async (options = {}) => {
    const refreshToken = sessionRef.current.refreshToken;
    if (!refreshToken) {
      if (!options.silent) notify("No refresh token is available.", "watch");
      return false;
    }
    updateLoading({ refresh: true });
    try {
      const payload = await requestJson("admin/auth/refresh", {
        method: "POST",
        body: JSON.stringify({ refreshToken }),
      });
      applySession(payload);
      if (!options.silent) notify("Admin token refreshed.", "ok");
      return true;
    } catch (error) {
      clearSession();
      notify(`Refresh failed: ${error.message}`, "risk");
      return false;
    } finally {
      updateLoading({ refresh: false });
    }
  }, [applySession, clearSession, notify, updateLoading]);

  const apiJson = React.useCallback(async (path, options = {}) => {
    const activeToken = sessionRef.current.token;
    try {
      return await requestJson(path, { ...options, token: activeToken });
    } catch (error) {
      if (error.status === 401 && !options.skipRefresh && await refreshAdminSession({ silent: true })) {
        return requestJson(path, { ...options, skipRefresh: true, token: sessionRef.current.token });
      }
      throw error;
    }
  }, [refreshAdminSession]);

  const refreshHealth = React.useCallback(async () => {
    updateLoading({ health: true });
    setHealth((current) => ({ ...current, status: "Checking", detail: "Probing /api/health" }));
    try {
      const result = await probeHealth(sessionRef.current.token);
      setHealth(result);
    } catch (error) {
      setHealth({
        ok: false,
        status: "Offline",
        detail: error.message,
        latencyMs: 0,
        checkedAt: new Date().toLocaleTimeString(),
      });
    } finally {
      updateLoading({ health: false });
    }
  }, [updateLoading]);

  const fetchDashboard = React.useCallback(async () => {
    if (!sessionRef.current.token) {
      notify("Login or paste an admin token to load live data.", "watch");
      return;
    }
    updateLoading({ dashboard: true });
    try {
      const snapshot = await apiJson("admin/dashboard");
      setDashboard(mapDashboard(snapshot));
      setLastUpdated(formatTime(snapshot.generatedAt || Date.now()));
      notify("Dashboard refreshed from MongoDB.", "ok");
    } catch (error) {
      notify(`Dashboard refresh failed: ${error.message}`, "risk");
    } finally {
      updateLoading({ dashboard: false });
    }
  }, [apiJson, notify, updateLoading]);

  React.useEffect(() => {
    clearLegacyStoredTokens();
    refreshHealth();
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, [refreshHealth]);

  async function loginAdmin(event) {
    event.preventDefault();
    const username = authForm.username.trim();
    const password = authForm.password;
    if (!username || !password) {
      notify("Username and password are required.", "watch");
      return;
    }
    updateLoading({ login: true });
    try {
      const payload = await requestJson("admin/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      });
      applySession(payload);
      notify("Admin session established.", "ok");
      await fetchDashboard();
    } catch (error) {
      notify(`Login failed: ${error.message}`, "risk");
    } finally {
      updateLoading({ login: false });
    }
  }

  async function usePastedToken() {
    const token = authForm.token.trim();
    if (!token) {
      clearSession();
      notify("Admin token cleared from this tab.", "watch");
      return;
    }
    const nextSession = {
      username: authForm.username.trim() || sessionRef.current.username || INITIAL_USERNAME,
      token,
      refreshToken: "",
      tokenExpiresAt: Date.now() + 50 * 60 * 1000,
    };
    sessionRef.current = nextSession;
    setSession(nextSession);
    setAuthForm((current) => ({ ...current, token: "" }));
    localStorage.setItem(USERNAME_STORAGE_KEY, nextSession.username);
    notify("Admin token loaded for this tab.", "ok");
    await fetchDashboard();
  }

  async function refreshAll() {
    await refreshHealth();
    await fetchDashboard();
  }

  async function recordAdminAction(action, payload) {
    updateLoading({ action });
    try {
      const response = await apiJson("admin/actions", {
        method: "POST",
        body: JSON.stringify({ action, payload }),
      });
      notify(response.accepted ? `${action} recorded.` : `${action} rejected.`, response.accepted ? "ok" : "watch");
      if (response.accepted) await fetchDashboard();
    } catch (error) {
      notify(`Action failed: ${error.message}`, "risk");
    } finally {
      updateLoading({ action: "" });
    }
  }

  async function handleModuleAction(action) {
    if (action === "probe-health") {
      await refreshHealth();
      return;
    }
    if (action === "refresh-token") {
      await refreshAdminSession();
      return;
    }
    if (SENSITIVE_ACTIONS.has(action) && !secure) {
      notify("Sensitive admin action blocked until HTTPS is enabled.", "risk");
      return;
    }

    const payload = buildActionPayload(action, dashboard, runtimeState);
    if (SENSITIVE_ACTIONS.has(action)) {
      if (!payload) {
        notify("Live dashboard data or required form input is missing for this admin action.", "watch");
        return;
      }
      await recordAdminAction(action, payload);
      return;
    }

    if (!payload) {
      notify("No live data is available for this action.", "watch");
      return;
    }
    await copyText(typeof payload === "string" ? payload : JSON.stringify(payload, null, 2), notify);
  }

  function updateAuthField(field, value) {
    setAuthForm((current) => ({ ...current, [field]: value }));
  }

  return html`
    <div className="app-shell">
      <aside className="sidebar" aria-label="Admin sections">
        <div className="brand-block">
          <div className="brand-mark">PL</div>
          <div>
            <div className="brand-name">Project Lumen</div>
            <div className="brand-subtitle">Admin Dashboard</div>
          </div>
        </div>

        <nav className="nav-list">
          ${adminSections.map((section) => html`
            <button
              key=${section.id}
              className=${`nav-button ${section.id === sectionId ? "active" : ""}`}
              type="button"
              onClick=${() => setSectionId(section.id)}
            >
              <span>${section.title}</span>
              <span className="nav-count">${section.modules.length}</span>
            </button>
          `)}
        </nav>

        <div className="runtime-box">
          <div className="runtime-label">API base</div>
          <code>${API_BASE}</code>
          <div className=${`runtime-status ${secure ? "ok" : "risk"}`}>
            ${secure ? "Secure admin transport" : "Sensitive actions locked"}
          </div>
        </div>

        <div className="sidebar-summary" aria-label="Module status summary">
          ${["ok", "watch", "risk", "info"].map((status) => html`
            <div className="sidebar-summary-row" key=${status}>
              <span>${statusLabel(status)}</span>
              <strong>${statusCounts[status] || 0}</strong>
            </div>
          `)}
        </div>
      </aside>

      <main className="workspace">
        <header className="topbar">
          <div className="title-block">
            <div className="eyebrow">
              <${LayoutDashboard} size=${16} aria-hidden="true" />
              <span>${environment} · last ${range} days</span>
            </div>
            <h1>${selectedSection.title}</h1>
            <p>${selectedSection.subtitle}</p>
            <div className="title-meta">
              <${SessionChip} session=${session} now=${now} secure=${secure} />
              ${lastUpdated ? html`<span>Updated ${lastUpdated}</span>` : html`<span>Live data not loaded</span>`}
            </div>
          </div>

          <form className="auth-panel" onSubmit=${loginAdmin}>
            <label className="token-field">
              <span>Username</span>
              <input
                type="text"
                autoComplete="username"
                value=${authForm.username}
                onInput=${(event) => updateAuthField("username", event.currentTarget.value)}
              />
            </label>
            <label className="token-field">
              <span>Password</span>
              <input
                type="password"
                autoComplete="current-password"
                value=${authForm.password}
                onInput=${(event) => updateAuthField("password", event.currentTarget.value)}
              />
            </label>
            <button className="button primary button-with-icon" type="submit" disabled=${loading.login}>
              <${LogIn} size=${15} aria-hidden="true" />
              <span>${loading.login ? "Signing in" : "Login"}</span>
            </button>
            <label className="token-field token-field-wide">
              <span>Admin token</span>
              <input
                type="password"
                autoComplete="off"
                placeholder="Bearer token"
                value=${authForm.token}
                onInput=${(event) => updateAuthField("token", event.currentTarget.value)}
              />
            </label>
            <button className="button secondary button-with-icon" type="button" onClick=${usePastedToken}>
              <${KeyRound} size=${15} aria-hidden="true" />
              <span>Use token</span>
            </button>
            <button
              className="button secondary button-with-icon"
              type="button"
              disabled=${loading.refresh || !session.refreshToken}
              onClick=${() => refreshAdminSession()}
            >
              <${RefreshCw} size=${15} aria-hidden="true" />
              <span>${loading.refresh ? "Refreshing" : "Refresh token"}</span>
            </button>
            <button className="button primary button-with-icon" type="button" disabled=${loading.dashboard || loading.health} onClick=${refreshAll}>
              <${RefreshCw} size=${15} aria-hidden="true" />
              <span>${loading.dashboard || loading.health ? "Refreshing" : "Refresh"}</span>
            </button>
          </form>
        </header>

        ${secure ? null : html`
          <section className="security-banner" role="alert">
            <div>
              <strong>HTTPS required for admin operations.</strong>
              <span>This dashboard disables sensitive actions on non-local HTTP origins.</span>
            </div>
            <button
              className="button danger button-with-icon"
              type="button"
              onClick=${() => copyText("Admin operations require HTTPS outside localhost.", notify)}
            >
              <${ShieldAlert} size=${15} aria-hidden="true" />
              <span>Copy note</span>
            </button>
          </section>
        `}

        <section className="status-grid" aria-label="Service summary">
          ${summaryCards.map((item) => html`<${MetricCard} key=${item.id} item=${item} />`)}
        </section>

        <section className="controls-strip" aria-label="Dashboard controls">
          <label className="search-field">
            <span>Search modules</span>
            <div className="input-with-icon">
              <${Search} size=${16} aria-hidden="true" />
              <input
                type="search"
                placeholder="Search users, crashes, templates, releases"
                value=${query}
                onInput=${(event) => setQuery(event.currentTarget.value)}
              />
            </div>
          </label>
          <label className="select-field">
            <span>Environment</span>
            <div className="input-with-icon">
              <${SlidersHorizontal} size=${16} aria-hidden="true" />
              <select value=${environment} onChange=${(event) => setEnvironment(event.currentTarget.value)}>
                <option value="production">Production</option>
                <option value="staging">Staging</option>
                <option value="local">Local</option>
              </select>
            </div>
          </label>
          <label className="select-field">
            <span>Range</span>
            <div className="input-with-icon">
              <${Gauge} size=${16} aria-hidden="true" />
              <select value=${range} onChange=${(event) => setRange(event.currentTarget.value)}>
                <option value="7">Last 7 days</option>
                <option value="30">Last 30 days</option>
                <option value="90">Last 90 days</option>
              </select>
            </div>
          </label>
        </section>

        <section className="section-strip" aria-label="Current section modules">
          ${selectedSection.modules.map((module) => html`
            <button
              key=${module.kind}
              className="section-chip"
              type="button"
              onClick=${() => setQuery(module.title)}
            >
              <span>${module.kicker}</span>
              <${StatusPill} status=${module.status} />
            </button>
          `)}
        </section>

        <section className="module-grid" aria-live="polite">
          ${visibleModules.length
            ? visibleModules.map((module) => html`
                <${ModuleCard}
                  key=${module.kind}
                  module=${module}
                  data=${dashboard}
                  state=${runtimeState}
                  secure=${secure}
                  onAction=${handleModuleAction}
                  busyAction=${loading.action}
                />
              `)
            : html`<div className="empty-state wide">No modules match the current filter.</div>`}
        </section>
      </main>
      <${ToastStack} toasts=${toasts} />
    </div>
  `;
}

function MetricCard({ item }) {
  const Icon = {
    health: item.status === "ok" ? CheckCircle2 : Gauge,
    crashes: AlertTriangle,
    sync: RefreshCw,
    access: ShieldCheck,
    release: LayoutDashboard,
  }[item.id] || Gauge;
  return html`
    <article className=${`metric-card metric-${normalizeStatus(item.status)}`}>
      <div className="metric-icon"><${Icon} size=${18} aria-hidden="true" /></div>
      <span className="metric-label">${item.label}</span>
      <strong>${item.value}</strong>
      <small>${item.detail}${item.id === "health" && item.latencyMs ? ` | ${item.latencyMs} ms` : ""}</small>
    </article>
  `;
}

function SessionChip({ session, now, secure }) {
  const tokenReady = Boolean(session.token);
  const expired = Boolean(session.tokenExpiresAt && session.tokenExpiresAt <= now);
  const status = !secure ? "risk" : tokenReady && !expired ? "ok" : "watch";
  const label = tokenReady && !expired ? `${session.username} connected` : `${session.username} not connected`;
  return html`
    <span className=${`session-chip session-${status}`}>
      ${secure ? html`<${ShieldCheck} size=${14} aria-hidden="true" />` : html`<${ShieldAlert} size=${14} aria-hidden="true" />`}
      <span>${expired ? "Token expired" : label}</span>
    </span>
  `;
}

function ToastStack({ toasts }) {
  return html`
    <div className="toast-stack" role="status" aria-live="polite">
      ${toasts.map((toast) => html`
        <div className=${`toast toast-${normalizeStatus(toast.tone)}`} key=${toast.id}>
          ${toast.message}
        </div>
      `)}
    </div>
  `;
}

function matchesQuery(module, query) {
  const normalized = query.trim().toLowerCase();
  if (!normalized) return true;
  return `${module.title} ${module.kicker} ${module.kind}`.toLowerCase().includes(normalized);
}

function buildActionPayload(action, data, state) {
  const selectedPlanUserId = readField("planUserIdInput") || data.profile?.id || "";
  const selectedTier = readField("planTierInput") || "PRO";
  const selectedProductId = readField("planProductIdInput") || "manual_admin_pro";
  const selectedExpiresAt = Number(readField("planExpiresAtInput") || 0);
  const firstRelease = data.releases?.[0];
  const firstBackup = data.backups?.[0];
  const firstCrash = data.crashes?.[0];

  const payloads = {
    "open-user": data.profile?.id ? data.profile : null,
    "export-devices": data.devices.length ? data.devices : null,
    "copy-audit": data.accessAudit.length ? data.accessAudit : null,
    "change-plan": selectedPlanUserId ? {
      userId: selectedPlanUserId,
      tier: selectedTier,
      productId: selectedProductId,
      expiresAt: Number.isFinite(selectedExpiresAt) ? selectedExpiresAt : 0,
    } : null,
    "revoke-pro": selectedPlanUserId ? { userId: selectedPlanUserId } : null,
    "download-backup": firstBackup || null,
    "copy-backup": firstBackup?.summary || null,
    "export-crashes": data.crashes.length
      ? data.crashes.map((crash) => `- ${crash.group}: ${crash.count} events on ${crash.version}`).join("\n")
      : null,
    "copy-crash-key": firstCrash?.group || null,
    "copy-stack": data.stack.length ? data.stack.join("\n") : null,
    "version-matrix": data.versionAnalysis.length ? data.versionAnalysis : null,
    "copy-sync-report": data.syncMetrics.length ? {
      rangeDays: Number(state.range),
      averagePayloadKb: Math.round(avg(data.syncSeries)),
      samples: data.syncMetrics,
    } : null,
    "push-template": buildTemplatePayload(data),
    "copy-template-json": data.templates.length ? data.templates : null,
    "preview-template": buildTemplatePayload(data),
    "copy-audio": data.audioMatrix.length ? data.audioMatrix : null,
    "queue-i18n": data.i18nJobs.length ? data.i18nJobs : null,
    "copy-telemetry": data.telemetry.length ? data.telemetry : null,
    "upload-checksums": data.releases.length
      ? data.releases.map((release) => ({ versionCode: release.version, sha256: release.sha }))
      : null,
    "force-update": firstRelease ? {
      versionCode: Number(firstRelease.version || 0),
      versionName: firstRelease.name || "admin-policy",
      sha256: firstRelease.sha || "",
      rollout: "blocked",
      forceUpdate: true,
    } : null,
    "copy-rollout": data.rolloutPlan.length ? data.rolloutPlan : null,
    "copy-routes": data.routes.length ? data.routes : null,
    "save-allowlist": buildAllowlistPayload(data),
    "copy-security": "HTTP admin traffic is blocked outside localhost. Move admin access to HTTPS-only.",
  };

  return cloneValue(payloads[action] || null);
}

function buildTemplatePayload(data) {
  const existing = data.templateEditor || {};
  const name = readField("templateNameInput") || existing.name || "";
  const color = readField("templateColorInput") || existing.color || "";
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
      ...(existing.layoutJson || {}),
      titleText,
      subtitleText,
      countdownStyle: style,
      showSkipButton,
    },
  };
}

function buildAllowlistPayload(data) {
  const existing = data.allowlist?.[0] || {};
  const origin = readField("allowlistOriginInput") || existing.origin || "";
  const protocol = readField("allowlistProtocolInput") || existing.protocol || "https";
  const risk = readField("allowlistRiskInput") || existing.risk || "required";
  return origin ? { origin, protocol, risk } : null;
}

function parseLocaleList(value) {
  return String(value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function readField(id) {
  return document.getElementById(id)?.value?.trim() || "";
}

async function copyText(text, notify) {
  try {
    await navigator.clipboard.writeText(text);
    notify("Copied.", "ok");
  } catch {
    notify("Clipboard unavailable.", "risk");
  }
}

const rootElement = document.getElementById("root");
createRoot(rootElement).render(html`<${AdminDashboardApp} />`);
