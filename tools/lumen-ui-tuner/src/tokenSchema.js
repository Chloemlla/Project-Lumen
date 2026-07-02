export const controlGroups = [
  {
    title: "Top bar layout",
    controls: [
      slider("topBar.primaryTitleStartDp", "Primary title start", 0, 72, 1, "dp"),
      slider("topBar.secondaryLeadingWidthDp", "Secondary leading width", 40, 64, 1, "dp"),
      slider("topBar.containerStartPaddingDp", "Container start padding", 0, 24, 1, "dp"),
      slider("topBar.containerEndPaddingDp", "Container end padding", 0, 32, 1, "dp"),
      slider("topBar.contentTopGapDp", "Top gap", 0, 24, 1, "dp"),
      slider("topBar.contentHeightDp", "Content height", 48, 72, 1, "dp"),
      slider("topBar.contentBottomGapDp", "Bottom gap", 0, 24, 1, "dp"),
      slider("topBar.collapseThresholdDp", "Collapse threshold", 24, 160, 1, "dp"),
    ],
  },
  {
    title: "Top bar text",
    controls: [
      slider("topBar.titleFontSizeSp", "Title size", 12, 24, 0.5, "sp"),
      slider("topBar.titleFontWeight", "Title weight", 400, 800, 100, ""),
      slider("topBar.titleMaxLines", "Title max lines", 1, 3, 1, ""),
      text("topBar.samplePrimaryTitle", "Primary sample title"),
      text("topBar.sampleSecondaryTitle", "Secondary sample title"),
    ],
  },
  {
    title: "Preview colors",
    controls: [
      color("preview.backgroundColor", "Background"),
      color("preview.surfaceColor", "Surface"),
      color("preview.primaryColor", "Top bar"),
      color("preview.onPrimaryColor", "On top bar"),
      color("preview.outlineColor", "Outline"),
      color("preview.textColor", "Text"),
    ],
  },
  {
    title: "Page frame",
    controls: [
      slider("page.maxContentWidthDp", "Max content width", 320, 840, 4, "dp"),
      slider("page.contentPaddingStartDp", "Content start padding", 0, 32, 1, "dp"),
      slider("page.contentPaddingTopDp", "Content top padding", 0, 32, 1, "dp"),
      slider("page.contentPaddingEndDp", "Content end padding", 0, 32, 1, "dp"),
      slider("page.contentPaddingBottomDp", "Content bottom padding", 0, 40, 1, "dp"),
      slider("page.sectionGapDp", "Section gap", 4, 28, 1, "dp"),
    ],
  },
  {
    title: "Preview text",
    controls: [
      text("previewText.cardKicker", "Card kicker"),
      text("previewText.cardTitle", "Card title"),
      text("previewText.metricRest", "Rest metric"),
      text("previewText.metricFocus", "Focus metric"),
      text("previewText.metricBlink", "Blink metric"),
      text("previewText.metricLight", "Light metric"),
    ],
  },
];

export function cloneTokens(tokens) {
  return JSON.parse(JSON.stringify(tokens));
}

export function readPath(source, path) {
  return path.split(".").reduce((value, key) => value?.[key], source);
}

export function updatePath(source, path, nextValue) {
  const next = cloneTokens(source);
  const keys = path.split(".");
  const last = keys.pop();
  const target = keys.reduce((value, key) => value[key], next);
  target[last] = nextValue;
  return next;
}

export function stableJson(tokens) {
  return `${JSON.stringify(tokens, null, 2)}\n`;
}

export function topBarKotlinPreview(tokens) {
  const topBar = tokens.topBar;
  return [
    "LumenTopBarTokens(",
    `    containerStartPaddingDp = ${numberLiteral(topBar.containerStartPaddingDp)}f,`,
    `    containerEndPaddingDp = ${numberLiteral(topBar.containerEndPaddingDp)}f,`,
    `    contentTopGapDp = ${numberLiteral(topBar.contentTopGapDp)}f,`,
    `    contentHeightDp = ${numberLiteral(topBar.contentHeightDp)}f,`,
    `    contentBottomGapDp = ${numberLiteral(topBar.contentBottomGapDp)}f,`,
    `    collapseThresholdDp = ${numberLiteral(topBar.collapseThresholdDp)}f,`,
    `    primaryTitleStartDp = ${numberLiteral(topBar.primaryTitleStartDp)}f,`,
    `    secondaryLeadingWidthDp = ${numberLiteral(topBar.secondaryLeadingWidthDp)}f,`,
    `    titleFontSizeSp = ${numberLiteral(topBar.titleFontSizeSp)}f,`,
    `    titleFontWeight = ${Math.round(topBar.titleFontWeight)},`,
    `    titleMaxLines = ${Math.round(topBar.titleMaxLines)},`,
    ")",
  ].join("\n");
}

function slider(path, label, min, max, step, unit) {
  return { kind: "slider", path, label, min, max, step, unit };
}

function text(path, label) {
  return { kind: "text", path, label };
}

function color(path, label) {
  return { kind: "color", path, label };
}

function numberLiteral(value) {
  return Number(value).toFixed(Number.isInteger(value) ? 0 : 1);
}
