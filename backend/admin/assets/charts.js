import { React, html } from "./ui.js";

export function MiniChart({ series, tone = "accent", label = "Trend" }) {
  const canvasRef = React.useRef(null);
  const values = Array.isArray(series) ? series : [];
  const seriesKey = values.join(",");

  React.useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return undefined;

    const draw = () => drawLineChart(canvas, values, tone);
    draw();

    const resizeObserver = new ResizeObserver(draw);
    resizeObserver.observe(canvas);
    return () => resizeObserver.disconnect();
  }, [seriesKey, tone]);

  return html`<canvas className="mini-chart" ref=${canvasRef} role="img" aria-label=${label}></canvas>`;
}

function drawLineChart(canvas, series, tone) {
  const context = canvas.getContext("2d");
  const rect = canvas.getBoundingClientRect();
  const ratio = window.devicePixelRatio || 1;
  const width = Math.max(Math.floor(rect.width), 280);
  const height = Math.max(Math.floor(rect.height), 160);
  const styles = getComputedStyle(document.documentElement);
  const color = token(styles, `--chart-${tone}`) || token(styles, "--chart-accent") || "CanvasText";
  const grid = token(styles, "--chart-grid") || "GrayText";
  const muted = token(styles, "--muted") || "GrayText";
  const data = series.length ? series : [0];
  const max = Math.max(...data, 1);
  const padding = 24;

  canvas.width = width * ratio;
  canvas.height = height * ratio;
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
  context.clearRect(0, 0, width, height);

  context.strokeStyle = grid;
  context.lineWidth = 1;
  for (let index = 0; index < 4; index += 1) {
    const y = padding + ((height - padding * 2) / 3) * index;
    context.beginPath();
    context.moveTo(padding, y);
    context.lineTo(width - padding, y);
    context.stroke();
  }

  if (!series.length) {
    context.fillStyle = muted;
    context.font = "12px ui-sans-serif, system-ui, sans-serif";
    context.fillText("No samples", padding, height / 2);
    return;
  }

  context.strokeStyle = color;
  context.lineWidth = 3;
  context.lineJoin = "round";
  context.lineCap = "round";
  context.beginPath();
  data.forEach((value, index) => {
    const point = chartPoint(value, index, data.length, max, width, height, padding);
    if (index === 0) context.moveTo(point.x, point.y);
    else context.lineTo(point.x, point.y);
  });
  context.stroke();

  context.fillStyle = color;
  data.forEach((value, index) => {
    const point = chartPoint(value, index, data.length, max, width, height, padding);
    context.beginPath();
    context.arc(point.x, point.y, 4, 0, Math.PI * 2);
    context.fill();
  });
}

function chartPoint(value, index, count, max, width, height, padding) {
  return {
    x: padding + ((width - padding * 2) / Math.max(count - 1, 1)) * index,
    y: height - padding - (value / max) * (height - padding * 2),
  };
}

function token(styles, name) {
  return styles.getPropertyValue(name).trim();
}
