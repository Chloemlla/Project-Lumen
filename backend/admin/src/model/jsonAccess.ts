import type { JsonRecord, JsonValue } from "../types";

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function readRecord(source: unknown, key: string): Record<string, unknown> {
  if (!isRecord(source)) return {};
  const value = source[key];
  return isRecord(value) ? value : {};
}

export function readUnknown(source: unknown, key: string): unknown {
  if (!isRecord(source)) return undefined;
  return source[key];
}

export function readArray(source: unknown, key: string): unknown[] {
  if (!isRecord(source)) return [];
  const value = source[key];
  return Array.isArray(value) ? value : [];
}

export function readString(source: unknown, key: string, fallback = ""): string {
  if (!isRecord(source)) return fallback;
  const value = source[key];
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return fallback;
}

export function readNumber(source: unknown, key: string, fallback = 0): number {
  if (!isRecord(source)) return fallback;
  const value = source[key];
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

export function readBoolean(source: unknown, key: string, fallback = false): boolean {
  if (!isRecord(source)) return fallback;
  const value = source[key];
  return typeof value === "boolean" ? value : fallback;
}

export function readStringArray(source: unknown, key: string): string[] {
  return readArray(source, key)
    .map((item) => (typeof item === "string" ? item : ""))
    .filter(Boolean);
}

export function readJsonRecord(source: unknown, key: string): JsonRecord {
  if (!isRecord(source)) return {};
  const value = toJsonValue(source[key]);
  return isJsonRecord(value) ? value : {};
}

export function toJsonValue(value: unknown): JsonValue {
  if (
    value === null
    || typeof value === "string"
    || typeof value === "number"
    || typeof value === "boolean"
  ) {
    return value;
  }

  if (Array.isArray(value)) {
    return value.map((item) => toJsonValue(item));
  }

  if (!isRecord(value)) {
    return null;
  }

  return Object.fromEntries(
    Object.entries(value).map(([key, item]) => [key, toJsonValue(item)]),
  );
}

function isJsonRecord(value: JsonValue): value is JsonRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
