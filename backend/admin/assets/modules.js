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
        <div>${formRows([
          ["Plan tier", "select", ["FREE", "PRO", "PLUS", "TEAM", "DEVELOPER"]],
          ["Entitlement", "select", ["pro_templates", "advanced_statistics", "cloud_sync", "advanced_export"]],
          ["Expires at", "text", "0 means lifetime"],
        ])}</div>
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

  function renderSessionSecurity(ctx) {
    const expires = ctx.state.tokenExpiresAt > Date.now() ? new Date(ctx.state.tokenExpiresAt).toLocaleString() : "Not set";
    return kvTable([
      ["Transport", ctx.isSecureAdminOrigin() ? tag("secure", "ok") : tag("locked", "risk")],
      ["Token stored", ctx.state.token ? tag("yes", "ok") : tag("no", "watch")],
      ["Token expiry", expires],
      ["Refresh strategy", "401-aware refresh hook; admin API refresh endpoint pending"],
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
