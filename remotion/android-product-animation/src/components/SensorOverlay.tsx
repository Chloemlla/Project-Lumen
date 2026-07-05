import type {DemoScene, SignalMetric} from "../data/androidDemoState";

type SensorOverlayProps = {
  scene: DemoScene;
};

export function SensorOverlay({scene}: SensorOverlayProps) {
  return (
    <section className="sensor-panel">
      <div className="sensor-face">
        <div className="face-outline">
          <span className="eye left" />
          <span className="eye right" />
          <span className="scan-line" />
        </div>
      </div>
      <div className="signal-list">
        {scene.signals.map((signal) => (
          <SignalRow key={`${scene.id}-${signal.label}`} signal={signal} />
        ))}
      </div>
      <div className="capability-row">
        {scene.capabilities.map((capability) => (
          <span key={`${scene.id}-${capability}`}>{capability}</span>
        ))}
      </div>
    </section>
  );
}

function SignalRow({signal}: {signal: SignalMetric}) {
  return (
    <div className="signal-row">
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
