import { API_BASE } from "./dashboard-model.js";

export async function requestJson(path, options = {}) {
  const response = await fetch(`${API_BASE}/${path}`, {
    method: options.method || "GET",
    headers: {
      Accept: "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
    },
    body: options.body,
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : {};
  if (!response.ok) {
    throw httpError(response.status, payload?.error?.message || `HTTP ${response.status}`);
  }
  return payload;
}

export async function probeHealth(token) {
  const startedAt = performance.now();
  const payload = await requestJson("health", { token });
  return {
    ok: true,
    status: payload.status || "ok",
    detail: `${payload.service || "project-lumen-api"} ${payload.version || ""}`.trim(),
    latencyMs: Math.round(performance.now() - startedAt),
    checkedAt: new Date().toLocaleTimeString(),
  };
}

function httpError(status, message) {
  const error = new Error(message);
  error.status = status;
  return error;
}
