import type {CSSProperties} from "react";
import {
  androidDemoState,
  metricTrendLabel,
  surfaceItemStatusLabel,
} from "../data/androidDemoState";
import type {
  DemoScene,
  NavigationKey,
  PhoneMetric,
  SurfaceItem,
} from "../data/androidDemoState";

type PhoneFrameProps = {
  scene: DemoScene;
  progress: number;
};

type RingStyle = CSSProperties & {
  "--ring-angle": string;
};

const navItems: {key: NavigationKey; label: string}[] = [
  {key: "HOME", label: "首页"},
  {key: "BREAK", label: "休息"},
  {key: "POMODORO", label: "番茄钟"},
  {key: "STATS", label: "统计"},
  {key: "SETTINGS", label: "设置"},
];

export function PhoneFrame({scene, progress}: PhoneFrameProps) {
  const ringAngle = Math.round(scene.phone.progress * 360);
  const ringStyle: RingStyle = {
    "--ring-angle": `${ringAngle}deg`,
  };
  const surface = scene.phone.surface;
  const chartMax = Math.max(...surface.chart, 1);

  return (
    <div className="phone-shell">
      <div className="phone-screen">
        <div className="phone-status">
          <span>09:42</span>
          <span>5G</span>
          <span>100%</span>
        </div>
        <div className="phone-topbar">
          <span>Project Lumen</span>
          <span>{scene.phone.status}</span>
        </div>
        <div className="phone-app-surface">
          <div className="phone-hero">
            <div className="timer-ring" style={ringStyle}>
              <div className="timer-ring-inner">
                <strong>{scene.phone.timer}</strong>
                <span>{scene.phone.title}</span>
              </div>
            </div>
            <div className="surface-copy">
              <span>{surface.mode}</span>
              <strong>{surface.headline}</strong>
              <em>{surface.subline}</em>
            </div>
          </div>
          <button className="primary-phone-action" type="button">
            {surface.primaryAction}
          </button>
          <div className="metric-grid">
            {scene.phone.metrics.map((metric) => (
              <MetricTile key={`${scene.id}-${metric.label}`} metric={metric} />
            ))}
          </div>
          <div className="surface-row-list">
            {surface.rows.slice(0, 4).map((item) => (
              <SurfaceRow item={item} key={`${scene.id}-${item.label}`} />
            ))}
          </div>
          <div className="surface-toggle-list">
            {surface.toggles.slice(0, 3).map((item) => (
              <SurfaceToggle item={item} key={`${scene.id}-${item.label}`} />
            ))}
          </div>
          <div className="surface-chart" aria-label={`${scene.phone.title} chart`}>
            {surface.chart.map((value, index) => (
              <span
                key={`${scene.id}-chart-${String(index)}`}
                style={{height: `${Math.max(16, Math.round((value / chartMax) * 88))}px`}}
              />
            ))}
          </div>
          {surface.files.length > 0 ? (
            <div className="surface-files">
              {surface.files.map((file) => (
                <span key={`${scene.id}-${file}`}>{file}</span>
              ))}
            </div>
          ) : null}
          <div className="surface-timeline">
            {surface.timeline.map((step, index) => (
              <span
                className={index / Math.max(surface.timeline.length - 1, 1) <= progress ? "active" : ""}
                key={`${scene.id}-${step}`}
              >
                {step}
              </span>
            ))}
          </div>
        </div>
        <div className="phone-progress">
          <span>{androidDemoState.productName} 章节进度</span>
          <div className="progress-track">
            <div className="progress-fill" style={{width: `${Math.round(progress * 100)}%`}} />
          </div>
        </div>
        <nav className="phone-nav" aria-label="Project Lumen demo navigation">
          {navItems.map((item) => (
            <span className={item.key === scene.phone.activeNav ? "selected" : ""} key={item.key}>
              {item.label}
            </span>
          ))}
        </nav>
      </div>
    </div>
  );
}

function MetricTile({metric}: {metric: PhoneMetric}) {
  return (
    <div className="metric-tile">
      <span>{metric.label}</span>
      <strong>{metric.value}</strong>
      <em>{metricTrendLabel(metric)}</em>
    </div>
  );
}

function SurfaceRow({item}: {item: SurfaceItem}) {
  const progress = item.progress ?? 0;

  return (
    <div className={`surface-row status-${item.status ?? "on"}`}>
      <div>
        <span>{item.label}</span>
        <strong>{item.value}</strong>
        <em>{item.detail}</em>
      </div>
      <div className="surface-row-meter">
        <b style={{width: `${Math.round(progress * 100)}%`}} />
      </div>
    </div>
  );
}

function SurfaceToggle({item}: {item: SurfaceItem}) {
  return (
    <div className={`surface-toggle status-${item.status ?? "on"}`}>
      <span>{item.label}</span>
      <strong>{surfaceItemStatusLabel(item)}</strong>
    </div>
  );
}
