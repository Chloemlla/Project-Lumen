import {signalLevel} from "../data/androidDemoState";
import type {DemoScene, SignalMetric} from "../data/androidDemoState";

type SensorOverlayProps = {
  scene: DemoScene;
};

export function SensorOverlay({scene}: SensorOverlayProps) {
  return (
    <section className="sensor-panel">
      <div className="sensor-heading">
        <span>{scene.phone.surface.mode}</span>
        <strong>{scene.spotlight.label}</strong>
        <em>{scene.spotlight.value}</em>
      </div>
      <div className="sensor-face">
        <div className="face-outline">
          <span className="eye left" />
          <span className="eye right" />
          <span className="scan-line" />
          <span className="face-bracket top-left" />
          <span className="face-bracket top-right" />
          <span className="face-bracket bottom-left" />
          <span className="face-bracket bottom-right" />
        </div>
      </div>
      <div className="signal-list">
        {scene.signals.map((signal) => (
          <SignalRow key={`${scene.id}-${signal.label}`} signal={signal} />
        ))}
      </div>
      <div className="sensor-stack">
        {scene.flowNodes.slice(0, 4).map((node) => (
          <span key={`${scene.id}-${node}`}>{node}</span>
        ))}
      </div>
      <div className="capability-row">
        {scene.capabilities.slice(0, 6).map((capability) => (
          <span key={`${scene.id}-${capability}`}>{capability}</span>
        ))}
      </div>
    </section>
  );
}

function SignalRow({signal}: {signal: SignalMetric}) {
  return (
    <div className={`signal-row level-${signalLevel(signal)}`}>
      <div className="signal-copy">
        <span>{signal.label}</span>
        <strong>{signal.value}</strong>
      </div>
      <div className="progress-track">
        <div className="progress-fill" style={{width: `${Math.round(signal.progress * 100)}%`}} />
      </div>
    </div>
  );
}
