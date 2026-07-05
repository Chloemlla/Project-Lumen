import type {CSSProperties} from "react";
import type {DemoScene, PhoneMetric} from "../data/androidDemoState";

type PhoneFrameProps = {
  scene: DemoScene;
  progress: number;
};

type RingStyle = CSSProperties & {
  "--ring-angle": string;
};

export function PhoneFrame({scene, progress}: PhoneFrameProps) {
  const ringAngle = Math.round(scene.phone.progress * 360);
  const ringStyle: RingStyle = {
    "--ring-angle": `${ringAngle}deg`,
  };

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
        <div className="phone-hero">
          <div className="timer-ring" style={ringStyle}>
            <div className="timer-ring-inner">
              <strong>{scene.phone.timer}</strong>
              <span>{scene.phone.title}</span>
            </div>
          </div>
        </div>
        <div className="metric-grid">
          {scene.phone.metrics.map((metric) => (
            <MetricTile key={`${scene.id}-${metric.label}`} metric={metric} />
          ))}
        </div>
        <div className="phone-progress">
          <span>运行进度</span>
          <div className="progress-track">
            <div className="progress-fill" style={{width: `${Math.round(progress * 100)}%`}} />
          </div>
        </div>
        <nav className="phone-nav" aria-label="Project Lumen demo navigation">
          <span className={scene.id === "home" ? "selected" : ""}>首页</span>
          <span className={scene.id === "break-loop" ? "selected" : ""}>休息</span>
          <span className={scene.id === "pomodoro" ? "selected" : ""}>番茄钟</span>
          <span className={scene.id === "reports" ? "selected" : ""}>统计</span>
          <span className={scene.id === "advanced" ? "selected" : ""}>设置</span>
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
    </div>
  );
}
