window.LumenAdminModules = (() => {
  const data = window.LumenAdminData;

  function bodyForKind(kind, ctx) {
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
      sessionSecurity: () => renderSessionSecurity(ctx),
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
    if (!data.devices.length) return `<div class="empty-state">No registered device assets have been recorded.</div>`;
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
    const users = data.users?.length
      ? data.users
      : (data.profile?.id ? [{ id: data.profile.id, email: data.profile.email, planTier: data.profile.planTier }] : []);
    const entitlementRows = (data.entitlements || []).map((entry) => [
      `<code>${escapeHtml(entry.userId)}</code>`,
      entry.product,
      tag(entry.tier, entry.status === "active" ? "ok" : "watch"),
      tag(entry.status, entry.status === "active" ? "ok" : "risk"),
      entry.expiresAt,
    ]);
    return `
      <div class="split-panel">
        <div>
          ${users.length ? "" : `<div class="empty-state">No users are available for entitlement changes.</div>`}
          <label class="form-row">
            <span>User</span>
            <select id="planUserIdInput">
              ${users.map((user) => `<option value="${escapeHtml(user.id)}">${escapeHtml(user.email)} | ${escapeHtml(user.planTier || "FREE")}</option>`).join("")}
            </select>
          </label>
          <label class="form-row">
            <span>Plan tier</span>
            <select id="planTierInput">
              ${["PRO", "PLUS", "TEAM", "DEVELOPER", "FREE"].map((tier) => `<option>${tier}</option>`).join("")}
            </select>
          </label>
          <label class="form-row"><span>Product ID</span><input id="planProductIdInput" type="text" value="manual_admin_pro"></label>
          <label class="form-row"><span>Expires at</span><input id="planExpiresAtInput" type="number" value="0"></label>
        </div>
        <div>
          ${entitlementRows.length ? table(["User", "Product", "Tier", "Status", "Expires"], entitlementRows) : `<div class="empty-state">No entitlement records have been recorded.</div>`}
          <div class="timeline">${data.purchaseAudit.map((entry) => timelineItem(entry.time, `${entry.product} | ${entry.status} | ${entry.token}`, entry.action)).join("")}</div>
        </div>
      </div>
    `;
  }

  function renderBackups() {
    if (!data.backups.length) return `<div class="empty-state">No cloud backups have been uploaded yet.</div>`;
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
    const rows = data.versionAnalysis || [];
    return `
      <canvas class="mini-chart" data-chart="crashes" width="540" height="180"></canvas>
      ${rows.length ? table(["Version", "Manufacturer", "Crashes", "Affected", "Trend"], rows.map((item) => [
        item.version,
        item.manufacturer,
        item.crashes,
        item.affected,
        tag(item.trend, item.risk),
      ])) : `<div class="empty-state">No version impact telemetry has been recorded.</div>`}
    `;
  }

  function renderApiHealth() {
    const rows = (data.apiMetrics || []).map((metric) => [
      metric.endpoint,
      `${metric.qps || 0}`,
      `${metric.p95Ms || 0} ms`,
      `${metric.status2xx || 0}/${metric.status4xx || 0}/${metric.status5xx || 0}`,
    ]);
    return `
      <canvas class="mini-chart" data-chart="api" width="540" height="180"></canvas>
      ${rows.length ? table(["Endpoint", "QPS", "p95", "2xx/4xx/5xx"], rows) : `<div class="empty-state">No API metric samples have been recorded.</div>`}
    `;
  }

  function renderSyncThroughput() {
    const rows = (data.syncMetrics || []).map((metric) => [
      metric.endpoint,
      `${metric.averagePayloadKb || 0} KB`,
      `${metric.largestPayloadKb || 0} KB`,
      `${metric.p95Ms || 0} ms`,
      metric.rejectedPayloads || 0,
    ]);
    return `
      <canvas class="mini-chart" data-chart="sync" width="540" height="180"></canvas>
      ${rows.length ? table(["Endpoint", "Average", "Largest", "p95", "Rejected"], rows) : `<div class="empty-state">No sync throughput samples have been recorded.</div>`}
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
    const template = data.templateEditor || {};
    const layout = template.layoutJson || {};
    return `
      <div class="split-panel">
        <div>${formRows([
          ["Rest title", "text", layout.titleText || template.name || ""],
          ["Rest subtitle", "text", layout.subtitleText || ""],
          ["Countdown style", "select", [template.style || "circle", "bar", "number"]],
          ["Skip button", "select", [layout.showSkipButton === false ? "hide" : "show", layout.showSkipButton === false ? "show" : "hide"]],
        ])}</div>
        <pre class="json-view">${escapeHtml(JSON.stringify(layout, null, 2))}</pre>
      </div>
    `;
  }

  function renderAudioMatrix() {
    const cells = data.audioMatrix || [];
    if (!cells.length) return `<div class="empty-state">No audio or haptic telemetry has been recorded.</div>`;
    return `<div class="matrix">${cells.map((cell) => `<div class="matrix-cell"><div class="row-title">${escapeHtml(cell.label)}</div><div class="row-meta">${escapeHtml(cell.value)} | ${escapeHtml(cell.meta)}</div></div>`).join("")}</div>`;
  }

  function renderI18n() {
    const jobs = data.i18nJobs || [];
    if (!jobs.length) return `<div class="empty-state">No localized template dispatch data has been recorded.</div>`;
    return `
      <div class="list-stack">
        ${jobs.map((job) => row(
          `${job.locale} template pack`,
          `${job.templateCount} templates, ${job.premiumCount} premium`,
          tag(job.status, job.status === "ready" ? "ok" : "watch"),
        )).join("")}
      </div>
    `;
  }

  function renderTelemetry() {
    return `
      <canvas class="mini-chart" data-chart="telemetry" width="540" height="180"></canvas>
      <div class="list-stack">${data.telemetry.map((item) => {
        const externalSource = item.label.startsWith("External SDK source:");
        const detail = externalSource
          ? `${item.value} calls in last ${item.rangeDays || 7}d`
          : `${item.value}% of anonymous aggregate cohort`;
        return row(item.label, detail, tag(externalSource ? String(item.value) : `${item.value}%`, "info"));
      }).join("")}</div>
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
    const items = data.rolloutPlan || [];
    if (!items.length) return `<div class="empty-state">No release rollout policy has been recorded.</div>`;
    return `
      <div class="list-stack">
        ${items.map((item) => row(item.title, item.detail, tag(statusLabel(item.status), item.status))).join("")}
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

  function renderSessionSecurity(ctx) {
    const expires = ctx.state.tokenExpiresAt > Date.now() ? new Date(ctx.state.tokenExpiresAt).toLocaleString() : "Not set";
    return kvTable([
      ["Transport", ctx.isSecureAdminOrigin() ? tag("secure", "ok") : tag("locked", "risk")],
      ["Token stored", ctx.state.token ? tag("yes", "ok") : tag("no", "watch")],
      ["Token expiry", expires],
      ["Refresh strategy", "401-aware refresh hook backed by /api/admin/auth/refresh"],
    ]);
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
    return { ok: "OK", watch: "Watch", risk: "Risk", info: "Info" }[status] || status;
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  return { actionsForKind, bodyForKind, escapeHtml, statusLabel };
})();
