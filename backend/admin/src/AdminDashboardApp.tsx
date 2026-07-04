import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent } from "react";
import { AdminHttpError, probeHealth, requestJson } from "./api/adminApi";
import { DashboardControls } from "./components/DashboardControls";
import { EmptyState } from "./components/common";
import { ModuleCard } from "./components/modules/ModuleCard";
import {
  MetricCard,
  SecurityBanner,
  Sidebar,
  ToastStack,
  Topbar,
} from "./components/ShellPanels";
import { adminSections } from "./data/adminSections";
import { buildActionPayload } from "./model/actionPayloads";
import {
  SENSITIVE_ACTIONS,
  SERVER_ADMIN_ACTIONS,
  buildDashboardSummary,
  clearLegacyStoredTokens,
  createDashboardData,
  formatTime,
  isSecureAdminOrigin,
  mapDashboard,
  moduleStatusCounts,
  parseAdminSession,
} from "./model/dashboardModel";
import { isRecord, readBoolean, readNumber, readString } from "./model/jsonAccess";
import type { AdminSession, HealthState, JsonValue, LoadingState, ToastItem } from "./types";

const USERNAME_STORAGE_KEY = "projectLumenAdminUsername";
const INITIAL_USERNAME = localStorage.getItem(USERNAME_STORAGE_KEY) || "admin";

type AuthFormState = {
  username: string;
  password: string;
  token: string;
};

type ApiJsonOptions = {
  method?: "GET" | "POST";
  body?: string;
  skipRefresh?: boolean;
};

export function AdminDashboardApp() {
  const [dashboard, setDashboard] = useState(() => createDashboardData());
  const [sectionId, setSectionId] = useState("users");
  const [query, setQuery] = useState("");
  const [environment, setEnvironment] = useState("production");
  const [range, setRange] = useState("30");
  const [now, setNow] = useState(Date.now());
  const [lastUpdated, setLastUpdated] = useState("");
  const [health, setHealth] = useState<HealthState>({
    ok: false,
    status: "Unknown",
    detail: "No probe yet",
    latencyMs: 0,
    checkedAt: "",
  });
  const [authForm, setAuthForm] = useState<AuthFormState>({
    username: INITIAL_USERNAME,
    password: "",
    token: "",
  });
  const [session, setSession] = useState<AdminSession>({
    username: INITIAL_USERNAME,
    token: "",
    refreshToken: "",
    tokenExpiresAt: 0,
  });
  const [loading, setLoading] = useState<LoadingState>({
    health: false,
    dashboard: false,
    login: false,
    refresh: false,
    action: "",
  });
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const sessionRef = useRef(session);
  const secure = isSecureAdminOrigin();
  const selectedSection = useMemo(
    () => adminSections.find((section) => section.id === sectionId) ?? adminSections[0],
    [sectionId],
  );
  const statusCounts = useMemo(() => moduleStatusCounts(adminSections), []);

  useEffect(() => {
    sessionRef.current = session;
  }, [session]);

  const updateLoading = useCallback((patch: Partial<LoadingState>) => {
    setLoading((current) => ({ ...current, ...patch }));
  }, []);

  const notify = useCallback((message: string, tone: ToastItem["tone"] = "info") => {
    const id = `${Date.now()}-${Math.random()}`;
    setToasts((current) => [...current.slice(-2), { id, message, tone }]);
    window.setTimeout(() => {
      setToasts((current) => current.filter((toast) => toast.id !== id));
    }, 3200);
  }, []);

  const applySession = useCallback((nextSession: AdminSession) => {
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

  const clearSession = useCallback(() => {
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

  const refreshAdminSession = useCallback(async (options: { silent?: boolean } = {}) => {
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
      const parsed = parseAdminSession(payload, sessionRef.current.username || INITIAL_USERNAME);
      if (!parsed) throw new Error("Refresh response did not include an access token.");
      applySession(parsed);
      if (!options.silent) notify("Admin token refreshed.", "ok");
      return true;
    } catch (error) {
      clearSession();
      notify(`Refresh failed: ${messageFromError(error)}`, "risk");
      return false;
    } finally {
      updateLoading({ refresh: false });
    }
  }, [applySession, clearSession, notify, updateLoading]);

  const apiJson = useCallback(async (path: string, options: ApiJsonOptions = {}): Promise<unknown> => {
    const activeToken = sessionRef.current.token;
    try {
      return await requestJson(path, { method: options.method, body: options.body, token: activeToken });
    } catch (error) {
      if (
        error instanceof AdminHttpError
        && error.status === 401
        && !options.skipRefresh
        && await refreshAdminSession({ silent: true })
      ) {
        return requestJson(path, {
          method: options.method,
          body: options.body,
          token: sessionRef.current.token,
        });
      }
      throw error;
    }
  }, [refreshAdminSession]);

  const refreshHealth = useCallback(async () => {
    updateLoading({ health: true });
    setHealth((current) => ({ ...current, status: "Checking", detail: "Probing /api/health" }));
    try {
      setHealth(await probeHealth(sessionRef.current.token));
    } catch (error) {
      setHealth({
        ok: false,
        status: "Offline",
        detail: messageFromError(error),
        latencyMs: 0,
        checkedAt: new Date().toLocaleTimeString(),
      });
    } finally {
      updateLoading({ health: false });
    }
  }, [updateLoading]);

  const fetchDashboard = useCallback(async () => {
    if (!sessionRef.current.token) {
      notify("Login or paste an admin token to load live data.", "watch");
      return;
    }
    updateLoading({ dashboard: true });
    try {
      const snapshot = await apiJson("admin/dashboard");
      setDashboard(mapDashboard(snapshot));
      const generatedAt = readNumber(snapshot, "generatedAt") || readString(snapshot, "generatedAt");
      setLastUpdated(formatTime(generatedAt || Date.now()));
      notify("Dashboard refreshed from MongoDB.", "ok");
    } catch (error) {
      notify(`Dashboard refresh failed: ${messageFromError(error)}`, "risk");
    } finally {
      updateLoading({ dashboard: false });
    }
  }, [apiJson, notify, updateLoading]);

  useEffect(() => {
    clearLegacyStoredTokens();
    refreshHealth();
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, [refreshHealth]);

  async function loginAdmin(event: FormEvent<HTMLFormElement>) {
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
      const parsed = parseAdminSession(payload, username);
      if (!parsed) throw new Error("Login response did not include an access token.");
      applySession(parsed);
      notify("Admin session established.", "ok");
      await fetchDashboard();
    } catch (error) {
      notify(`Login failed: ${messageFromError(error)}`, "risk");
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
    updateLoading({ login: true });
    try {
      const operator = await requestJson("admin/me", { token });
      const nextSession = {
        username: readString(
          operator,
          "username",
          authForm.username.trim() || sessionRef.current.username || INITIAL_USERNAME,
        ),
        token,
        refreshToken: "",
        tokenExpiresAt: Date.now() + 50 * 60 * 1000,
      };
      applySession(nextSession);
      notify("Admin token validated for this tab.", "ok");
      await fetchDashboard();
    } catch (error) {
      clearSession();
      notify(`Token validation failed: ${messageFromError(error)}`, "risk");
    } finally {
      updateLoading({ login: false });
    }
  }

  async function refreshAll() {
    await refreshHealth();
    await fetchDashboard();
  }

  async function recordAdminAction(action: string, payload: JsonValue) {
    updateLoading({ action });
    try {
      const response = await apiJson("admin/actions", {
        method: "POST",
        body: JSON.stringify({ action, payload }),
      });
      const accepted = readBoolean(response, "accepted");
      notify(accepted ? `${action} recorded.` : `${action} rejected.`, accepted ? "ok" : "watch");
      if (accepted) await fetchDashboard();
    } catch (error) {
      notify(`Action failed: ${messageFromError(error)}`, "risk");
    } finally {
      updateLoading({ action: "" });
    }
  }

  async function handleModuleAction(action: string) {
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

    const runtimeState = {
      section: sectionId,
      query,
      environment,
      range,
      token: session.token,
      tokenExpiresAt: session.tokenExpiresAt,
      now,
    };
    const payload = buildActionPayload(action, dashboard, runtimeState);
    if (SERVER_ADMIN_ACTIONS.has(action)) {
      if (payload === null) {
        notify("Live dashboard data or required form input is missing for this admin action.", "watch");
        return;
      }
      await recordAdminAction(action, payload);
      return;
    }

    if (action === "download-backup") {
      if (payload === null) {
        notify("No backup snapshot is available to download.", "watch");
        return;
      }
      downloadJsonFile(payload, backupDownloadName(payload), notify);
      return;
    }

    if (payload === null) {
      notify("No live data is available for this action.", "watch");
      return;
    }
    await copyText(typeof payload === "string" ? payload : JSON.stringify(payload, null, 2), notify);
  }

  function updateAuthField(field: keyof AuthFormState, value: string) {
    setAuthForm((current) => ({ ...current, [field]: value }));
  }

  if (!selectedSection) return null;

  const visibleModules = selectedSection.modules.filter((module) => matchesQuery(module, query));
  const summaryCards = buildDashboardSummary(dashboard, health, secure, session);
  const runtimeState = {
    section: sectionId,
    query,
    environment,
    range,
    token: session.token,
    tokenExpiresAt: session.tokenExpiresAt,
    now,
  };

  return (
    <div className="app-shell">
      <Sidebar
        sections={adminSections}
        sectionId={sectionId}
        secure={secure}
        statusCounts={statusCounts}
        onSelect={setSectionId}
      />

      <main className="workspace">
        <Topbar
          section={selectedSection}
          environment={environment}
          range={range}
          lastUpdated={lastUpdated}
          session={session}
          now={now}
          secure={secure}
          authForm={authForm}
          loading={loading}
          onAuthField={updateAuthField}
          onLogin={loginAdmin}
          onUseToken={usePastedToken}
          onRefreshToken={() => { void refreshAdminSession(); }}
          onRefreshAll={() => { void refreshAll(); }}
        />

        {secure ? null : (
          <SecurityBanner onCopy={() => { void copyText("Admin operations require HTTPS outside localhost.", notify); }} />
        )}

        <section className="status-grid" aria-label="Service summary">
          {summaryCards.map((item) => <MetricCard key={item.id} item={item} health={health} />)}
        </section>

        <DashboardControls
          query={query}
          environment={environment}
          range={range}
          selectedSection={selectedSection}
          onQuery={setQuery}
          onEnvironment={setEnvironment}
          onRange={setRange}
        />

        <section className="module-grid" aria-live="polite">
          {visibleModules.length ? visibleModules.map((module) => (
            <ModuleCard
              key={module.kind}
              module={module}
              data={dashboard}
              state={runtimeState}
              secure={secure}
              session={session}
              onAction={(action) => { void handleModuleAction(action); }}
              busyAction={loading.action}
            />
          )) : (
            <EmptyState wide label="No modules match the current filter." />
          )}
        </section>
      </main>

      <ToastStack toasts={toasts} />
    </div>
  );
}

function downloadJsonFile(
  payload: JsonValue,
  fileName: string,
  notify: (message: string, tone?: ToastItem["tone"]) => void,
): void {
  try {
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
    notify("Backup JSON downloaded.", "ok");
  } catch (error) {
    notify(`Download failed: ${messageFromError(error)}`, "risk");
  }
}

function backupDownloadName(payload: JsonValue): string {
  const id = isRecord(payload) ? String(payload.id || "backup") : "backup";
  const safeId = id.replace(/[^a-z0-9_.-]+/gi, "-").replace(/^-+|-+$/g, "") || "backup";
  return `project-lumen-${safeId}.json`;
}

function matchesQuery(module: { title: string; kicker: string; kind: string }, query: string): boolean {
  const normalized = query.trim().toLowerCase();
  if (!normalized) return true;
  return `${module.title} ${module.kicker} ${module.kind}`.toLowerCase().includes(normalized);
}

async function copyText(
  text: string,
  notify: (message: string, tone?: ToastItem["tone"]) => void,
): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
    notify("Copied.", "ok");
  } catch {
    notify("Clipboard unavailable.", "risk");
  }
}

function messageFromError(error: unknown): string {
  return error instanceof Error ? error.message : "Unknown error";
}
