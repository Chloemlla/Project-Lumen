import { cloneTokens } from "./tokenSchema.js";

export function buildMetrics(tokens) {
  const topBar = tokens.topBar;
  const statusBarHeightDp = tokens.preview.statusBarHeightDp;
  const primaryTitleStartDp = topBar.containerStartPaddingDp + topBar.primaryTitleStartDp;
  const secondaryTitleStartDp = topBar.containerStartPaddingDp + topBar.secondaryLeadingWidthDp;
  return {
    primaryTitleStartDp,
    secondaryTitleStartDp,
    deltaDp: secondaryTitleStartDp - primaryTitleStartDp,
    expandedTopBarHeightDp:
      statusBarHeightDp + topBar.contentTopGapDp + topBar.contentHeightDp + topBar.contentBottomGapDp,
  };
}

export function mergeTokens(base, incoming) {
  return {
    ...cloneTokens(base),
    ...incoming,
    topBar: { ...base.topBar, ...incoming?.topBar },
    page: { ...base.page, ...incoming?.page },
    preview: { ...base.preview, ...incoming?.preview },
    previewText: { ...base.previewText, ...incoming?.previewText },
  };
}

export function colorOrFallback(value, fallback) {
  return value || fallback;
}

export function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}
