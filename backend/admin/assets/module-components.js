import {
  Activity,
  Archive,
  Clipboard,
  Code2,
  Copy,
  Database,
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
  Zap,
} from "lucide-react";
import { html } from "./ui.js";
import { MiniChart } from "./charts.js";
import {
  backupSummary,
  normalizeStatus,
  safeColor,
  shortFingerprint,
  statusLabel,
  tokenExpiryLabel,
} from "./dashboard-model.js";

const ACTIONS = {
  profile: [{ label: "Open user record", action: "open-user", icon: UserRound }],
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
  versionAnalysis: [{ label: "Open version matrix", action: "version-matrix", icon: Gauge }],
  apiHealth: [{ label: "Probe health", action: "probe-health", icon: RefreshCw }],
  syncThroughput: [{ label: "Copy sync report", action: "copy-sync-report", icon: Copy }],
  templateCms: [
    { label: "Push global changes", action: "push-template", sensitive: true, icon: UploadCloud },
    { label: "Copy template JSON", action: "copy-template-json", icon: Copy },
  ],
  templateEditor: [{ label: "Preview payload", action: "preview-template", icon: Play }],
  audioMatrix: [{ label: "Copy audio matrix", action: "copy-audio", icon: Clipboard }],
  i18n: [{ label: "Queue dispatch", action: "queue-i18n", icon: Globe2 }],
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

export function ModuleCard({ module, data, state, secure, onAction, busyAction }) {
  return html`
    <article className="module-card" data-kind=${module.kind}>
      <header className="module-header">
        <div>
          <div className="module-kicker">${module.kicker}</div>
          <h2>${module.title}</h2>
        </div>
        <${StatusPill} status=${module.status} />
      </header>
      <div className="module-body">
        <${ModuleBody} kind=${module.kind} data=${data} state=${state} secure=${secure} />
      </div>
      <div className="module-actions">
        <${ModuleActions}
          kind=${module.kind}
          secure=${secure}
          onAction=${onAction}
          busyAction=${busyAction}
        />
      </div>
    </article>
  `;
}

export function ModuleActions({ kind, secure, onAction, busyAction }) {
  const actions = ACTIONS[kind] || [];
  return html`
    <div className="inline-actions">
      ${actions.map((item) => html`
        <${ActionButton}
          key=${item.action}
          item=${item}
          secure=${secure}
          onAction=${onAction}
          busy=${busyAction === item.action}
        />
      `)}
    </div>
  `;
}

function ActionButton({ item, secure, onAction, busy }) {
  const Icon = item.icon || Play;
  const disabled = Boolean(busy || (item.sensitive && !secure));
  const title = item.sensitive && !secure ? "Requires HTTPS or localhost" : item.label;
  return html`
    <button
      className=${`button ${item.variant || "secondary"} button-with-icon`}
      type="button"
      disabled=${disabled}
      title=${title}
      aria-label=${title}
      onClick=${() => onAction(item.action)}
    >
      <${Icon} size=${15} strokeWidth=${2.1} aria-hidden="true" />
      <span>${busy ? "Working" : item.label}</span>
    </button>
  `;
}

function ModuleBody({ kind, data, state, secure }) {
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
    sessionSecurity: () => renderSessionSecurity(data, state, secure),
  };
  return (renderers[kind] || (() => html`<${EmptyState} label="No module renderer is available." />`))(data, state, secure);
}

function renderProfile(data) {
  return html`
    <div className="module-stack">
      <${DataTable}
        headers=${["Field", "Value"]}
        rows=${[
          ["Email", data.profile.email],
          ["Registered", data.profile.registeredAt],
          ["Last sync", data.profile.lastSyncAt],
          ["Plan tier", html`<${Tag} text=${data.profile.planTier} status="ok" />`],
          ["Local security", data.profile.localSecurity],
        ]}
      />
      ${data.profile.featureFlags.length
        ? html`<div className="list-stack">${data.profile.featureFlags.map((flag) => html`
            <${ListRow}
              key=${flag}
              title=${flag}
              meta="Feature flag enabled"
              right=${html`<${Tag} text="enabled" status="ok" />`}
            />
          `)}</div>`
        : html`<${EmptyState} compact=${true} label="No feature flags are enabled for the selected user." />`}
    </div>
  `;
}

function renderDevices(data) {
  if (!data.devices.length) {
    return html`<${EmptyState} label="No registered device assets have been recorded." />`;
  }
  return html`
    <div className="list-stack">
      ${data.devices.map((device) => html`
        <${ListRow}
          key=${device.id}
          title=${device.model}
          meta=${`${device.id} | fingerprint ${shortFingerprint(device.fingerprint)} | versionCode ${device.versionCode} | ${device.config}`}
          right=${html`<${Tag} text=${device.lastSeen} status="info" />`}
        />
      `)}
    </div>
  `;
}

function renderAccessAudit(data) {
  if (!data.accessAudit.length) {
    return html`<${EmptyState} label="No admin access audit entries have been recorded." />`;
  }
  return html`
    <${DataTable}
      headers=${["Time", "Endpoint", "IP", "Geo", "Status"]}
      rows=${data.accessAudit.map((entry) => [
        entry.time,
        html`<code>${entry.endpoint}</code>`,
        entry.ip,
        entry.geo,
        html`<${Tag} text=${String(entry.status)} status=${entry.status === 200 ? "ok" : "risk"} />`,
      ])}
    />
  `;
}

function renderPlan(data) {
  const users = data.users.length
    ? data.users
    : (data.profile.id ? [{ id: data.profile.id, email: data.profile.email, planTier: data.profile.planTier }] : []);
  const entitlementRows = data.entitlements.map((entry) => [
    html`<code>${entry.userId}</code>`,
    entry.product,
    html`<${Tag} text=${entry.tier} status=${entry.status === "active" ? "ok" : "watch"} />`,
    html`<${Tag} text=${entry.status} status=${entry.status === "active" ? "ok" : "risk"} />`,
    entry.expiresAt,
  ]);

  return html`
    <div className="split-panel">
      <div className="form-stack">
        ${users.length ? null : html`<${EmptyState} compact=${true} label="No users are available for entitlement changes." />`}
        <label className="form-row">
          <span>User</span>
          <select id="planUserIdInput" disabled=${!users.length}>
            ${users.map((user) => html`
              <option key=${user.id} value=${user.id}>${user.email} | ${user.planTier || "FREE"}</option>
            `)}
          </select>
        </label>
        <label className="form-row">
          <span>Plan tier</span>
          <select id="planTierInput" defaultValue="PRO">
            ${["PRO", "PLUS", "TEAM", "DEVELOPER", "FREE"].map((tier) => html`<option key=${tier} value=${tier}>${tier}</option>`)}
          </select>
        </label>
        <label className="form-row">
          <span>Product ID</span>
          <input id="planProductIdInput" type="text" defaultValue="manual_admin_pro" />
        </label>
        <label className="form-row">
          <span>Expires at</span>
          <input id="planExpiresAtInput" type="number" min="0" defaultValue="0" />
        </label>
      </div>
      <div className="module-stack">
        ${entitlementRows.length
          ? html`<${DataTable} headers=${["User", "Product", "Tier", "Status", "Expires"]} rows=${entitlementRows} />`
          : html`<${EmptyState} compact=${true} label="No entitlement records have been recorded." />`}
        ${data.purchaseAudit.length
          ? html`<div className="timeline">${data.purchaseAudit.map((entry) => html`
              <${TimelineItem}
                key=${`${entry.time}-${entry.product}-${entry.action}`}
                time=${entry.time}
                title=${entry.product}
                meta=${`${entry.status} | ${entry.token} | ${entry.action}`}
              />
            `)}</div>`
          : null}
      </div>
    </div>
  `;
}

function renderBackups(data) {
  if (!data.backups.length) {
    return html`<${EmptyState} label="No cloud backups have been uploaded yet." />`;
  }
  const latest = data.backups[0];
  return html`
    <div className="split-panel">
      <div className="timeline">
        ${data.backups.map((backup) => html`
          <${TimelineItem}
            key=${backup.id}
            time=${backup.uploadedAt}
            title=${backup.id}
            meta=${backupSummary(backup.summary)}
          />
        `)}
      </div>
      <pre className="json-view">${JSON.stringify(latest.summary, null, 2)}</pre>
    </div>
  `;
}

function renderCrashes(data) {
  if (!data.crashes.length) {
    return html`<${EmptyState} label="No crash groups have been recorded." />`;
  }
  return html`
    <${DataTable}
      headers=${["Group", "Version", "Count", "Affected", "Risk"]}
      rows=${data.crashes.map((crash) => [
        crash.group,
        crash.version,
        crash.count,
        crash.affected,
        html`<${Tag} text=${statusLabel(crash.risk)} status=${crash.risk} />`,
      ])}
    />
  `;
}

function renderStack(data) {
  if (!data.stack.length) {
    return html`<${EmptyState} label="No sanitized stack frames have been recorded." />`;
  }
  return html`
    <pre className="stack-view">
      ${data.stack.map((line, index) => html`
        <span key=${`${index}-${line}`} className=${stackLineClass(line)}>${line}</span>
      `)}
    </pre>
  `;
}

function renderVersionAnalysis(data) {
  return html`
    <div className="module-stack">
      <${MiniChart} series=${data.crashes.map((item) => item.count)} tone="danger" label="Crash count trend" />
      ${data.versionAnalysis.length
        ? html`<${DataTable}
            headers=${["Version", "Manufacturer", "Crashes", "Affected", "Trend"]}
            rows=${data.versionAnalysis.map((item) => [
              item.version,
              item.manufacturer,
              item.crashes,
              item.affected,
              html`<${Tag} text=${item.trend} status=${item.risk} />`,
            ])}
          />`
        : html`<${EmptyState} compact=${true} label="No version impact telemetry has been recorded." />`}
    </div>
  `;
}

function renderApiHealth(data) {
  const rows = data.apiMetrics.map((metric) => [
    metric.endpoint || "unknown",
    `${metric.qps || 0}`,
    `${metric.p95Ms || 0} ms`,
    `${metric.status2xx || 0}/${metric.status4xx || 0}/${metric.status5xx || 0}`,
  ]);
  return html`
    <div className="module-stack">
      <${MiniChart} series=${data.apiSeries} tone="accent" label="API p95 latency trend" />
      ${rows.length
        ? html`<${DataTable} headers=${["Endpoint", "QPS", "p95", "2xx/4xx/5xx"]} rows=${rows} />`
        : html`<${EmptyState} compact=${true} label="No API metric samples have been recorded." />`}
    </div>
  `;
}

function renderSyncThroughput(data) {
  const rows = data.syncMetrics.map((metric) => [
    metric.endpoint || "unknown",
    `${metric.averagePayloadKb || 0} KB`,
    `${metric.largestPayloadKb || 0} KB`,
    `${metric.p95Ms || 0} ms`,
    metric.rejectedPayloads || 0,
  ]);
  return html`
    <div className="module-stack">
      <${MiniChart} series=${data.syncSeries} tone="info" label="Sync payload trend" />
      ${rows.length
        ? html`<${DataTable} headers=${["Endpoint", "Average", "Largest", "p95", "Rejected"]} rows=${rows} />`
        : html`<${EmptyState} compact=${true} label="No sync throughput samples have been recorded." />`}
    </div>
  `;
}

function renderTemplateCms(data) {
  if (!data.templates.length) {
    return html`<${EmptyState} label="No remote templates have been recorded." />`;
  }
  return html`
    <${DataTable}
      headers=${["Template", "Tier", "Countdown", "Color", "Locale"]}
      rows=${data.templates.map((template) => [
        template.name,
        html`<${Tag} text=${template.tier} status=${template.tier === "FREE" ? "info" : "ok"} />`,
        template.style,
        html`<${ColorValue} value=${template.color} />`,
        template.locale || "not recorded",
      ])}
    />
  `;
}

function renderTemplateEditor(data) {
  const template = data.templateEditor || {};
  const layout = template.layoutJson || {};
  return html`
    <div className="split-panel">
      <div className="form-stack">
        <label className="form-row">
          <span>Template name</span>
          <input id="templateNameInput" type="text" defaultValue=${template.name || ""} />
        </label>
        <label className="form-row">
          <span>Tier</span>
          <select id="templateTierInput" defaultValue=${template.tier || "PRO"}>
            ${["PRO", "PLUS", "TEAM", "DEVELOPER", "FREE"].map((tier) => html`<option key=${tier} value=${tier}>${tier}</option>`)}
          </select>
        </label>
        <label className="form-row">
          <span>Color</span>
          <input id="templateColorInput" type="text" defaultValue=${template.color || ""} placeholder="#0f766e" />
        </label>
        <label className="form-row">
          <span>Rest title</span>
          <input id="templateTitleInput" type="text" defaultValue=${layout.titleText || ""} />
        </label>
        <label className="form-row">
          <span>Rest subtitle</span>
          <input id="templateSubtitleInput" type="text" defaultValue=${layout.subtitleText || ""} />
        </label>
        <label className="form-row">
          <span>Countdown style</span>
          <select id="templateStyleInput" defaultValue=${template.style || "circle"}>
            ${["circle", "bar", "number"].map((style) => html`<option key=${style} value=${style}>${style}</option>`)}
          </select>
        </label>
        <label className="form-row">
          <span>Skip button</span>
          <select id="templateSkipInput" defaultValue=${layout.showSkipButton === false ? "hide" : "show"}>
            <option value="show">show</option>
            <option value="hide">hide</option>
          </select>
        </label>
      </div>
      <pre className="json-view">${JSON.stringify(layout, null, 2)}</pre>
    </div>
  `;
}

function renderAudioMatrix(data) {
  if (!data.audioMatrix.length) {
    return html`<${EmptyState} label="No audio or haptic telemetry has been recorded." />`;
  }
  return html`
    <div className="matrix">
      ${data.audioMatrix.map((cell) => html`
        <div className="matrix-cell" key=${cell.label}>
          <div className="row-title">${cell.label}</div>
          <div className="row-meta">${cell.value} | ${cell.meta}</div>
        </div>
      `)}
    </div>
  `;
}

function renderI18n(data) {
  if (!data.i18nJobs.length) {
    return html`<${EmptyState} label="No localized template dispatch data has been recorded." />`;
  }
  return html`
    <div className="list-stack">
      ${data.i18nJobs.map((job) => html`
        <${ListRow}
          key=${job.locale}
          title=${`${job.locale} template pack`}
          meta=${`${job.templateCount} templates, ${job.premiumCount} premium | updated ${job.updatedAt}`}
          right=${html`<${Tag} text=${statusLabel(job.status)} status=${job.status} />`}
        />
      `)}
    </div>
  `;
}

function renderTelemetry(data) {
  return html`
    <div className="module-stack">
      <${MiniChart} series=${data.telemetry.map((item) => item.value)} tone="info" label="Telemetry aggregate trend" />
      ${data.telemetry.length
        ? html`<div className="list-stack">${data.telemetry.map((item) => {
            const externalSource = item.label.startsWith("External SDK source:");
            const detail = externalSource
              ? `${item.value} calls in last ${item.rangeDays || 7}d`
              : `${item.value}% of anonymous aggregate cohort`;
            return html`
              <${ListRow}
                key=${item.label}
                title=${item.label}
                meta=${detail}
                right=${html`<${Tag} text=${externalSource ? String(item.value) : `${item.value}%`} status="info" />`}
              />
            `;
          })}</div>`
        : html`<${EmptyState} compact=${true} label="No anonymous telemetry aggregates have been recorded." />`}
    </div>
  `;
}

function renderOta(data) {
  if (!data.releases.length) {
    return html`<${EmptyState} label="No release integrity records have been recorded." />`;
  }
  return html`
    <${DataTable}
      headers=${["versionCode", "Release", "SHA256", "Rollout", "Policy"]}
      rows=${data.releases.map((release) => [
        release.version,
        release.name,
        html`<code>${release.sha || "not recorded"}</code>`,
        release.rollout,
        html`<${Tag} text=${release.force ? "force update" : "normal"} status=${release.force ? "risk" : "ok"} />`,
      ])}
    />
  `;
}

function renderRollout(data) {
  if (!data.rolloutPlan.length) {
    return html`<${EmptyState} label="No release rollout policy has been recorded." />`;
  }
  return html`
    <div className="list-stack">
      ${data.rolloutPlan.map((item) => html`
        <${ListRow}
          key=${`${item.title}-${item.detail}`}
          title=${item.title}
          meta=${item.detail}
          right=${html`<${Tag} text=${statusLabel(item.status)} status=${item.status} />`}
        />
      `)}
    </div>
  `;
}

function renderRoutes(data) {
  if (!data.routes.length) {
    return html`<${EmptyState} label="No route health samples have been recorded." />`;
  }
  return html`
    <${DataTable}
      headers=${["Module", "Path", "State", "p95"]}
      rows=${data.routes.map((route) => [
        route.module,
        html`<code>${route.path}</code>`,
        html`<${Tag} text=${statusLabel(route.status)} status=${route.status} />`,
        route.p95,
      ])}
    />
  `;
}

function renderAllowlist(data) {
  const first = data.allowlist[0] || {};
  return html`
    <div className="split-panel">
      <div className="form-stack">
        <label className="form-row">
          <span>Origin</span>
          <input id="allowlistOriginInput" type="text" defaultValue=${first.origin || ""} placeholder="admin.example.com" />
        </label>
        <label className="form-row">
          <span>Protocol</span>
          <select id="allowlistProtocolInput" defaultValue=${first.protocol || "https"}>
            <option value="https">https</option>
            <option value="http">http</option>
          </select>
        </label>
        <label className="form-row">
          <span>Risk</span>
          <select id="allowlistRiskInput" defaultValue=${first.risk || "required"}>
            <option value="required">required</option>
            <option value="review">review</option>
            <option value="blocked">blocked</option>
          </select>
        </label>
      </div>
      ${data.allowlist.length
        ? html`<${DataTable}
            headers=${["Origin", "Protocol", "Risk"]}
            rows=${data.allowlist.map((entry) => [
              entry.origin,
              html`<${Tag} text=${entry.protocol} status=${entry.protocol === "https" ? "ok" : "risk"} />`,
              entry.risk,
            ])}
          />`
        : html`<${EmptyState} compact=${true} label="No admin origin allowlist records have been recorded." />`}
    </div>
  `;
}

function renderSessionSecurity(data, state, secure) {
  return html`
    <${DataTable}
      headers=${["Field", "Value"]}
      rows=${[
        ["Transport", html`<${Tag} text=${secure ? "secure" : "locked"} status=${secure ? "ok" : "risk"} />`],
        ["Token in memory", html`<${Tag} text=${state.token ? "yes" : "no"} status=${state.token ? "ok" : "watch"} />`],
        ["Token expiry", tokenExpiryLabel(state.tokenExpiresAt)],
        ["Session storage", "admin tokens are not written to localStorage"],
      ]}
    />
  `;
}

export function StatusPill({ status }) {
  const tone = normalizeStatus(status);
  return html`<span className=${`status-pill status-${tone}`}>${statusLabel(status)}</span>`;
}

export function Tag({ text, status = "info" }) {
  const tone = normalizeStatus(status);
  return html`<span className=${`tag tag-${tone}`}>${text}</span>`;
}

function DataTable({ headers, rows }) {
  return html`
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>${headers.map((header) => html`<th key=${header}>${header}</th>`)}</tr>
        </thead>
        <tbody>
          ${rows.map((row, rowIndex) => html`
            <tr key=${rowIndex}>
              ${row.map((item, cellIndex) => html`<td key=${cellIndex}>${item}</td>`)}
            </tr>
          `)}
        </tbody>
      </table>
    </div>
  `;
}

function ListRow({ title, meta, right }) {
  return html`
    <div className="list-row">
      <div>
        <div className="row-title">${title}</div>
        <div className="row-meta">${meta}</div>
      </div>
      <div className="row-right">${right}</div>
    </div>
  `;
}

function TimelineItem({ time, title, meta }) {
  return html`
    <div className="timeline-item">
      <div className="row-title">${title}</div>
      <div className="row-meta">${time} | ${meta}</div>
    </div>
  `;
}

function EmptyState({ label, compact = false }) {
  return html`<div className=${compact ? "empty-state compact" : "empty-state"}>${label}</div>`;
}

function ColorValue({ value }) {
  const color = safeColor(value);
  return html`
    <span className="color-value">
      <span className="color-swatch" style=${color ? { background: color } : undefined}></span>
      <span>${value || "not recorded"}</span>
    </span>
  `;
}

function stackLineClass(line) {
  const value = String(line);
  if (value.includes("Exception")) return "stack-line keyword";
  if (value.includes("redacted")) return "stack-line path";
  return "stack-line";
}
