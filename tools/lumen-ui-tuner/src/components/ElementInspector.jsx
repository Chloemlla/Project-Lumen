import React from "react";
import { Crosshair, Info, MousePointer2, Type } from "lucide-react";
import { readPath } from "../tokenSchema.js";

export function ElementInspector({ selectedElement, tokens, metrics }) {
  const info = buildElementInfo(selectedElement, tokens, metrics);
  return (
    <section className="element-inspector">
      <div className="inspector-title">
        <div>
          <p className="eyebrow">Element</p>
          <h3>{info.name}</h3>
        </div>
        <Info size={18} />
      </div>
      <div className="inspector-grid">
        {info.fields.map(([label, value]) => (
          <React.Fragment key={label}>
            <span>{label}</span>
            <code>{value}</code>
          </React.Fragment>
        ))}
      </div>
      <div className="inspector-notes">
        {info.notes.map((note) => (
          <p key={note}>{note}</p>
        ))}
      </div>
      <div className="inspector-actions">
        <span><MousePointer2 size={15} /> Click selects</span>
        <span><Crosshair size={15} /> Drag title moves</span>
        <span><Type size={15} /> Edit text inline</span>
      </div>
    </section>
  );
}

function buildElementInfo(selectedElement, tokens, metrics) {
  const topBar = tokens.topBar;
  const page = tokens.page;
  const preview = tokens.preview;
  const textPath = {
    primaryTitle: "topBar.samplePrimaryTitle",
    secondaryTitle: "topBar.sampleSecondaryTitle",
    cardKicker: "previewText.cardKicker",
    cardTitle: "previewText.cardTitle",
    metricRest: "previewText.metricRest",
    metricFocus: "previewText.metricFocus",
    metricBlink: "previewText.metricBlink",
    metricLight: "previewText.metricLight",
  }[selectedElement];

  const sharedTextFields = textPath
    ? [
        ["Text token", textPath],
        ["Current text", readPath(tokens, textPath)],
      ]
    : [];

  const catalog = {
    primaryTitle: {
      name: "Primary top bar title",
      fields: [
        ["Compose", "LumenTopBar(title, onNavigateBack = null)"],
        ["Move token", "topBar.primaryTitleStartDp"],
        ["Start", `${metrics.primaryTitleStartDp}dp from screen start`],
        ["Leading spacer", `${topBar.primaryTitleStartDp}dp`],
        ["Font", `${topBar.titleFontSizeSp}sp / ${topBar.titleFontWeight}`],
        ...sharedTextFields,
      ],
      notes: [
        "Drag horizontally to tune the root page title start offset.",
        "Used by the UI tuner preview and LumenTopBarScreenshotTest title-alignment contract.",
        "Android LargeTopAppBar root pages use Material title insets; keep this slightly left of secondaryLeadingWidthDp.",
      ],
    },
    secondaryTitle: {
      name: "Secondary top bar title",
      fields: [
        ["Compose", "LumenTopBar(title, onNavigateBack = ...)"],
        ["Move token", "topBar.secondaryLeadingWidthDp"],
        ["Start", `${metrics.secondaryTitleStartDp}dp from screen start`],
        ["Back area", `${topBar.secondaryLeadingWidthDp}dp`],
        ["Font", `${topBar.titleFontSizeSp}sp / ${topBar.titleFontWeight}`],
        ...sharedTextFields,
      ],
      notes: [
        "Drag horizontally to tune the title start used when the back button exists.",
        "LumenTopBar sizes the back IconButton with secondaryLeadingWidthDp.",
      ],
    },
    backButton: {
      name: "Back button",
      fields: [
        ["Compose", "IconButton + ArrowBack"],
        ["Size token", "topBar.secondaryLeadingWidthDp"],
        ["Size", `${topBar.secondaryLeadingWidthDp}dp`],
        ["Tint", "MaterialTheme.onSurface via LargeTopAppBar colors"],
      ],
      notes: ["This element appears only on secondary pages."],
    },
    primaryTopBar: {
      name: "Primary top bar container",
      fields: topBarContainerFields(topBar, preview, metrics),
      notes: ["Container values are read from design/lumen-ui-tokens.json at app startup."],
    },
    secondaryTopBar: {
      name: "Secondary top bar container",
      fields: topBarContainerFields(topBar, preview, metrics),
      notes: ["Secondary pages share the same height, color, and typography tokens."],
    },
    pageContent: {
      name: "Page content frame",
      fields: [
        ["Compose", "LumenPage"],
        ["Max width", `${page.maxContentWidthDp}dp`],
        ["Padding", `${page.contentPaddingStartDp}/${page.contentPaddingTopDp}/${page.contentPaddingEndDp}/${page.contentPaddingBottomDp}dp`],
        ["Gap", `${page.sectionGapDp}dp`],
      ],
      notes: ["These tokens map to widthIn, PaddingValues, and Arrangement.spacedBy."],
    },
    summaryCard: {
      name: "Summary card preview",
      fields: [
        ["Web class", ".summary-card"],
        ["Android analog", "ActionCard / Card surfaces"],
        ["Border", "preview.outlineColor"],
        ["Surface", "preview.surfaceColor"],
      ],
      notes: ["This card is a visual preview target; it does not write Android card internals yet."],
    },
    listCard: {
      name: "List card preview",
      fields: [
        ["Web class", ".list-card"],
        ["Purpose", "Spacing and card density preview"],
        ["Surface", "preview.surfaceColor"],
      ],
      notes: ["Use this for layout rhythm checks after changing page spacing tokens."],
    },
    bottomNavigation: {
      name: "Bottom navigation preview",
      fields: [
        ["Android analog", "NavigationBar"],
        ["Height token", "preview.bottomNavigationHeightDp"],
        ["Height", `${preview.bottomNavigationHeightDp}dp`],
      ],
      notes: ["This is a preview-only dimension for the editor canvas."],
    },
  };

  if (textPath && !catalog[selectedElement]) {
    return {
      name: "Editable preview text",
      fields: [
        ["Token", textPath],
        ["Value", readPath(tokens, textPath)],
        ["Persistence", "Saved to exported JSON"],
      ],
      notes: ["This text supports inline editing in the web editor."],
    };
  }

  return catalog[selectedElement] || catalog.primaryTitle;
}

function topBarContainerFields(topBar, preview, metrics) {
  return [
    ["Compose", "LumenTopBar"],
    ["Height", `${metrics.expandedTopBarHeightDp}dp including preview status bar`],
    ["Content height", `${topBar.contentHeightDp}dp`],
    ["Top/bottom gap", `${topBar.contentTopGapDp}/${topBar.contentBottomGapDp}dp`],
    ["Horizontal padding", `${topBar.containerStartPaddingDp}/${topBar.containerEndPaddingDp}dp`],
    ["Collapse", `${topBar.collapseThresholdDp}dp`],
    ["Color", topBar.primaryColor || preview.primaryColor],
  ];
}
