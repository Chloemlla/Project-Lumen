import {
  AlertTriangle,
  CheckCircle2,
  Gauge,
  KeyRound,
  LayoutDashboard,
  LogIn,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import type { FormEvent } from "react";
import { API_BASE, normalizeStatus, statusLabel } from "../model/dashboardModel";
import type {
  AdminSection,
  AdminSession,
  HealthState,
  LoadingState,
  StatusTone,
  SummaryCard,
  ToastItem,
} from "../types";

type AuthFormState = {
  username: string;
  password: string;
  token: string;
};

const STATUS_SUMMARY_ORDER: StatusTone[] = ["ok", "watch", "risk", "info"];

export function Sidebar({ sections, sectionId, secure, statusCounts, onSelect }: {
  sections: AdminSection[];
  sectionId: string;
  secure: boolean;
  statusCounts: Partial<Record<StatusTone, number>>;
  onSelect: (id: string) => void;
}) {
  return (
    <aside className="sidebar" aria-label="Admin sections">
      <div className="brand-block">
        <div className="brand-mark">PL</div>
        <div>
          <div className="brand-name">Project Lumen</div>
          <div className="brand-subtitle">Admin Dashboard</div>
        </div>
      </div>

      <nav className="nav-list">
        {sections.map((section) => (
          <button
            key={section.id}
            className={`nav-button ${section.id === sectionId ? "active" : ""}`}
            type="button"
            onClick={() => onSelect(section.id)}
          >
            <span>{section.title}</span>
            <span className="nav-count">{section.modules.length}</span>
          </button>
        ))}
      </nav>

      <div className="runtime-box">
        <div className="runtime-label">API base</div>
        <code>{API_BASE}</code>
        <div className={`runtime-status ${secure ? "ok" : "risk"}`}>
          {secure ? "Secure admin transport" : "Sensitive actions locked"}
        </div>
      </div>

      <div className="sidebar-summary" aria-label="Module status summary">
        {STATUS_SUMMARY_ORDER.map((status) => (
          <div className="sidebar-summary-row" key={status}>
            <span>{statusLabel(status)}</span>
            <strong>{statusCounts[status] || 0}</strong>
          </div>
        ))}
      </div>
    </aside>
  );
}

export function Topbar({
  section,
  environment,
  range,
  lastUpdated,
  session,
  now,
  secure,
  authForm,
  loading,
  onAuthField,
  onLogin,
  onUseToken,
  onRefreshToken,
  onRefreshAll,
}: {
  section: AdminSection;
  environment: string;
  range: string;
  lastUpdated: string;
  session: AdminSession;
  now: number;
  secure: boolean;
  authForm: AuthFormState;
  loading: LoadingState;
  onAuthField: (field: keyof AuthFormState, value: string) => void;
  onLogin: (event: FormEvent<HTMLFormElement>) => void;
  onUseToken: () => void;
  onRefreshToken: () => void;
  onRefreshAll: () => void;
}) {
  return (
    <header className="topbar">
      <div className="title-block">
        <div className="eyebrow">
          <LayoutDashboard size={16} aria-hidden="true" />
          <span>{environment} - last {range} days</span>
        </div>
        <h1>{section.title}</h1>
        <p>{section.subtitle}</p>
        <div className="title-meta">
          <SessionChip session={session} now={now} secure={secure} />
          {lastUpdated ? <span>Updated {lastUpdated}</span> : <span>Live data not loaded</span>}
        </div>
      </div>

      <form className="auth-panel" onSubmit={onLogin}>
        <label className="token-field">
          <span>Username</span>
          <input
            type="text"
            autoComplete="username"
            value={authForm.username}
            onChange={(event) => onAuthField("username", event.currentTarget.value)}
          />
        </label>
        <label className="token-field">
          <span>Password</span>
          <input
            type="password"
            autoComplete="current-password"
            value={authForm.password}
            onChange={(event) => onAuthField("password", event.currentTarget.value)}
          />
        </label>
        <button className="button primary button-with-icon" type="submit" disabled={loading.login}>
          <LogIn size={15} aria-hidden="true" />
          <span>{loading.login ? "Signing in" : "Login"}</span>
        </button>
        <label className="token-field token-field-wide">
          <span>Admin token</span>
          <input
            type="password"
            autoComplete="off"
            placeholder="Bearer token"
            value={authForm.token}
            onChange={(event) => onAuthField("token", event.currentTarget.value)}
          />
        </label>
        <button className="button secondary button-with-icon" type="button" onClick={onUseToken}>
          <KeyRound size={15} aria-hidden="true" />
          <span>Use token</span>
        </button>
        <button
          className="button secondary button-with-icon"
          type="button"
          disabled={loading.refresh || !session.refreshToken}
          onClick={onRefreshToken}
        >
          <RefreshCw size={15} aria-hidden="true" />
          <span>{loading.refresh ? "Refreshing" : "Refresh token"}</span>
        </button>
        <button
          className="button primary button-with-icon"
          type="button"
          disabled={loading.dashboard || loading.health}
          onClick={onRefreshAll}
        >
          <RefreshCw size={15} aria-hidden="true" />
          <span>{loading.dashboard || loading.health ? "Refreshing" : "Refresh"}</span>
        </button>
      </form>
    </header>
  );
}

export function SecurityBanner({ onCopy }: { onCopy: () => void }) {
  return (
    <section className="security-banner" role="alert">
      <div>
        <strong>HTTPS required for admin operations.</strong>
        <span>This dashboard disables sensitive actions on non-local HTTP origins.</span>
      </div>
      <button className="button danger button-with-icon" type="button" onClick={onCopy}>
        <ShieldAlert size={15} aria-hidden="true" />
        <span>Copy note</span>
      </button>
    </section>
  );
}

export function MetricCard({ item, health }: { item: SummaryCard; health: HealthState }) {
  const icons: Record<string, LucideIcon> = {
    health: item.status === "ok" ? CheckCircle2 : Gauge,
    crashes: AlertTriangle,
    sync: RefreshCw,
    access: ShieldCheck,
    release: LayoutDashboard,
  };
  const Icon = icons[item.id] || Gauge;
  const latency = item.id === "health" && health.latencyMs ? ` | ${health.latencyMs} ms` : "";

  return (
    <article className={`metric-card metric-${normalizeStatus(item.status)}`}>
      <div className="metric-icon"><Icon size={18} aria-hidden="true" /></div>
      <span className="metric-label">{item.label}</span>
      <strong>{item.value}</strong>
      <small>{item.detail}{latency}</small>
    </article>
  );
}

export function SessionChip({ session, now, secure }: {
  session: AdminSession;
  now: number;
  secure: boolean;
}) {
  const tokenReady = Boolean(session.token);
  const expired = Boolean(session.tokenExpiresAt && session.tokenExpiresAt <= now);
  const status = !secure ? "risk" : tokenReady && !expired ? "ok" : "watch";
  const label = tokenReady && !expired ? `${session.username} connected` : `${session.username} not connected`;

  return (
    <span className={`session-chip session-${status}`}>
      {secure ? <ShieldCheck size={14} aria-hidden="true" /> : <ShieldAlert size={14} aria-hidden="true" />}
      <span>{expired ? "Token expired" : label}</span>
    </span>
  );
}

export function ToastStack({ toasts }: { toasts: ToastItem[] }) {
  return (
    <div className="toast-stack" role="status" aria-live="polite">
      {toasts.map((toast) => (
        <div className={`toast toast-${normalizeStatus(toast.tone)}`} key={toast.id}>
          {toast.message}
        </div>
      ))}
    </div>
  );
}
