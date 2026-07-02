import React, { useRef } from "react";
import { ArrowLeft } from "lucide-react";
import { clamp, colorOrFallback } from "../tokenUtils.js";

export function PhonePreview({ tokens, metrics, selectedElement, onSelectElement, onMoveTitle, onEditText }) {
  const topBar = tokens.topBar;
  const preview = tokens.preview;
  const page = tokens.page;
  const previewText = tokens.previewText;

  const phoneStyle = {
    "--phone-width": `${preview.deviceWidthDp}px`,
    "--top-bar-height": `${metrics.expandedTopBarHeightDp}px`,
    "--status-bar-height": `${preview.statusBarHeightDp}px`,
    "--top-bar-bg": colorOrFallback(topBar.primaryColor, preview.primaryColor),
    "--on-top-bar": colorOrFallback(topBar.onPrimaryColor, preview.onPrimaryColor),
    "--page-bg": preview.backgroundColor,
    "--surface": preview.surfaceColor,
    "--outline": preview.outlineColor,
    "--text": preview.textColor,
    "--content-start": `${page.contentPaddingStartDp}px`,
    "--content-top": `${page.contentPaddingTopDp}px`,
    "--content-end": `${page.contentPaddingEndDp}px`,
    "--content-bottom": `${page.contentPaddingBottomDp}px`,
    "--section-gap": `${page.sectionGapDp}px`,
    "--bottom-nav-height": `${preview.bottomNavigationHeightDp}px`,
  };

  return (
    <div className="phone-frame" style={phoneStyle}>
      <div className="phone-screen">
        <TopBarPreview
          title={topBar.samplePrimaryTitle}
          topBar={topBar}
          preview={preview}
          withBack={false}
          metrics={metrics}
          selectedElement={selectedElement}
          onSelectElement={onSelectElement}
          onMoveTitle={onMoveTitle}
          onEditText={onEditText}
        />
        <TopBarPreview
          title={topBar.sampleSecondaryTitle}
          topBar={topBar}
          preview={preview}
          withBack
          metrics={metrics}
          selectedElement={selectedElement}
          onSelectElement={onSelectElement}
          onMoveTitle={onMoveTitle}
          onEditText={onEditText}
        />
        <div
          className={selectedElement === "pageContent" ? "page-preview selected-element" : "page-preview"}
          onClick={(event) => select(event, onSelectElement, "pageContent")}
        >
          <article
            className={selectedElement === "summaryCard" ? "summary-card selected-element" : "summary-card"}
            onClick={(event) => select(event, onSelectElement, "summaryCard")}
          >
            <EditableText
              as="p"
              className="card-kicker editable-text"
              value={previewText.cardKicker}
              path="previewText.cardKicker"
              elementKey="cardKicker"
              selectedElement={selectedElement}
              onSelectElement={onSelectElement}
              onEditText={onEditText}
            />
            <EditableText
              as="h3"
              className="editable-text"
              value={previewText.cardTitle}
              path="previewText.cardTitle"
              elementKey="cardTitle"
              selectedElement={selectedElement}
              onSelectElement={onSelectElement}
              onEditText={onEditText}
            />
            <div className="metric-grid">
              {[
                ["metricRest", "previewText.metricRest"],
                ["metricFocus", "previewText.metricFocus"],
                ["metricBlink", "previewText.metricBlink"],
                ["metricLight", "previewText.metricLight"],
              ].map(([elementKey, path]) => (
                <EditableText
                  key={path}
                  as="span"
                  className="editable-text"
                  value={readPreviewText(tokens, elementKey)}
                  path={path}
                  elementKey={elementKey}
                  selectedElement={selectedElement}
                  onSelectElement={onSelectElement}
                  onEditText={onEditText}
                />
              ))}
            </div>
          </article>
          <article
            className={selectedElement === "listCard" ? "list-card selected-element" : "list-card"}
            onClick={(event) => select(event, onSelectElement, "listCard")}
          >
            <div />
            <div />
            <div />
          </article>
        </div>
        <div
          className={selectedElement === "bottomNavigation" ? "bottom-nav selected-element" : "bottom-nav"}
          onClick={(event) => select(event, onSelectElement, "bottomNavigation")}
        >
          <span />
          <span />
          <span />
          <span />
        </div>
      </div>
    </div>
  );
}

function TopBarPreview({
  title,
  topBar,
  preview,
  withBack,
  metrics,
  selectedElement,
  onSelectElement,
  onMoveTitle,
  onEditText,
}) {
  const titleStart = withBack ? metrics.secondaryTitleStartDp : metrics.primaryTitleStartDp;
  const elementKey = withBack ? "secondaryTitle" : "primaryTitle";
  const textPath = withBack ? "topBar.sampleSecondaryTitle" : "topBar.samplePrimaryTitle";
  const movePath = withBack ? "topBar.secondaryLeadingWidthDp" : "topBar.primaryTitleStartDp";
  const leadingWidth = withBack ? topBar.secondaryLeadingWidthDp : topBar.primaryTitleStartDp;
  const dragState = useRef(null);
  const titleClassName = [
    "title-text",
    "editable-text",
    "draggable-title",
    selectedElement === elementKey ? "selected-element" : "",
  ].filter(Boolean).join(" ");

  function handlePointerDown(event) {
    if (event.button !== 0) return;
    event.stopPropagation();
    onSelectElement(elementKey);
    event.currentTarget.setPointerCapture(event.pointerId);
    dragState.current = {
      startX: event.clientX,
      initialLeadingWidth: leadingWidth,
    };
  }

  function handlePointerMove(event) {
    if (event.buttons !== 1 || !dragState.current) return;
    event.stopPropagation();
    const deltaX = Math.round(event.clientX - dragState.current.startX);
    const nextLeadingWidth = clamp(
      dragState.current.initialLeadingWidth + deltaX,
      0,
      withBack ? 72 : 96,
    );
    onMoveTitle(movePath, nextLeadingWidth, withBack ? "Secondary title start" : "Primary title start");
  }

  function handlePointerUp(event) {
    if (dragState.current) {
      event.currentTarget.releasePointerCapture?.(event.pointerId);
    }
    dragState.current = null;
  }

  return (
    <div
      className={`top-bar-preview ${withBack ? "secondary" : "primary"}`}
      onClick={(event) => select(event, onSelectElement, withBack ? "secondaryTopBar" : "primaryTopBar")}
    >
      <div className="status-bar" />
      <div
        className="top-bar-content"
        style={{
          paddingLeft: `${topBar.containerStartPaddingDp}px`,
          paddingRight: `${topBar.containerEndPaddingDp}px`,
          height: `${topBar.contentHeightDp}px`,
          marginTop: `${topBar.contentTopGapDp}px`,
          marginBottom: `${topBar.contentBottomGapDp}px`,
        }}
      >
        {withBack ? (
          <button
            type="button"
            className={selectedElement === "backButton" ? "back-button selected-element" : "back-button"}
            style={{ width: `${topBar.secondaryLeadingWidthDp}px`, height: `${topBar.secondaryLeadingWidthDp}px` }}
            onClick={(event) => select(event, onSelectElement, "backButton")}
          >
            <ArrowLeft size={22} />
          </button>
        ) : (
          <div style={{ width: `${topBar.primaryTitleStartDp}px` }} />
        )}
        <EditableText
          as="div"
          className={titleClassName}
          value={title}
          path={textPath}
          elementKey={elementKey}
          selectedElement={selectedElement}
          onSelectElement={onSelectElement}
          onEditText={onEditText}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          style={{
            fontSize: `${topBar.titleFontSizeSp}px`,
            fontWeight: topBar.titleFontWeight,
            color: colorOrFallback(topBar.onPrimaryColor, preview.onPrimaryColor),
            WebkitLineClamp: topBar.titleMaxLines,
          }}
        />
      </div>
      <div className="alignment-line" style={{ left: `${titleStart}px` }} />
    </div>
  );
}

function EditableText({
  as = "span",
  className = "",
  value,
  path,
  elementKey,
  selectedElement,
  onSelectElement,
  onEditText,
  onPointerDown,
  onPointerMove,
  onPointerUp,
  style,
}) {
  const Component = as;
  return (
    <Component
      className={[
        className,
        selectedElement === elementKey ? "selected-element" : "",
      ].filter(Boolean).join(" ")}
      contentEditable
      suppressContentEditableWarning
      spellCheck={false}
      onClick={(event) => select(event, onSelectElement, elementKey)}
      onBlur={(event) => {
        const text = event.currentTarget.textContent.trim();
        if (text && text !== value) {
          onEditText(path, text, "Text");
        }
      }}
      onKeyDown={(event) => {
        if (event.key === "Enter") {
          event.preventDefault();
          event.currentTarget.blur();
        }
      }}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      style={style}
      title={path}
    >
      {value}
    </Component>
  );
}

function select(event, onSelectElement, key) {
  event.stopPropagation();
  onSelectElement(key);
}

function readPreviewText(tokens, key) {
  return {
    metricRest: tokens.previewText.metricRest,
    metricFocus: tokens.previewText.metricFocus,
    metricBlink: tokens.previewText.metricBlink,
    metricLight: tokens.previewText.metricLight,
  }[key];
}
