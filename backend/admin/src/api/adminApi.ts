import { API_BASE } from "../model/dashboardModel";
import type { HealthState } from "../types";
import { isRecord, readString } from "../model/jsonAccess";

export class AdminHttpError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "AdminHttpError";
    this.status = status;
  }
}

type RequestOptions = {
  method?: "GET" | "POST";
  body?: string;
  token?: string;
};

export async function requestJson(path: string, options: RequestOptions = {}): Promise<unknown> {
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
  const payload: unknown = text ? JSON.parse(text) : {};
  if (!response.ok) {
    throw new AdminHttpError(response.status, errorMessage(payload, `HTTP ${response.status}`));
  }
  return payload;
}

export async function probeHealth(token: string): Promise<HealthState> {
  const startedAt = performance.now();
  const payload = await requestJson("health", { token });
  return {
    ok: true,
    status: readString(payload, "status", "ok"),
    detail: `${readString(payload, "service", "project-lumen-api")} ${readString(payload, "version")}`.trim(),
    latencyMs: Math.round(performance.now() - startedAt),
    checkedAt: new Date().toLocaleTimeString(),
  };
}

function errorMessage(payload: unknown, fallback: string): string {
  const nestedMessage = readString(readError(payload), "message");
  return nestedMessage || fallback;
}

function readError(payload: unknown): Record<string, unknown> {
  if (!isRecord(payload)) return {};
  const error = payload.error;
  return isRecord(error) ? error : {};
}
