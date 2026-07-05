import {
  androidDemoState,
  demoScenes,
  type DemoScene,
} from "../data/androidDemoState";

type ScenePanelProps = {
  scene: DemoScene;
};

export function ScenePanel({scene}: ScenePanelProps) {
  return (
    <aside className="scene-panel">
      <div className="scene-copy">
        <span className="eyebrow">{scene.eyebrow}</span>
        <h1>{scene.title}</h1>
        <p>{scene.subtitle}</p>
      </div>
      <div className="voiceover-card">
        <span>中文旁白</span>
        <strong>{scene.voiceover}</strong>
      </div>
      <div className="scene-list">
        {demoScenes.map((item, index) => (
          <div
            className={`scene-list-item ${item.id === scene.id ? "active" : ""}`}
            key={item.id}
          >
            <span>{String(index + 1).padStart(2, "0")}</span>
            <strong>{item.title}</strong>
          </div>
        ))}
      </div>
      <div className="slogan-strip">
        <span>{androidDemoState.productName}</span>
        <strong>{androidDemoState.slogan}</strong>
      </div>
    </aside>
  );
}
