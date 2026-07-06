import {
  androidDemoState,
  demoScenes,
  type DemoScene,
} from "../data/androidDemoState";

type ScenePanelProps = {
  scene: DemoScene;
  progress: number;
};

export function ScenePanel({scene, progress}: ScenePanelProps) {
  const neighborScenes = demoScenes.filter(
    (item) => Math.abs(item.chapter - scene.chapter) <= 1,
  );

  return (
    <aside className="scene-panel">
      <div className="scene-copy">
        <span className="eyebrow">{scene.eyebrow}</span>
        <h2>{scene.chapterTitle}</h2>
        <p>{scene.voiceover}</p>
      </div>
      <div className="voiceover-card">
        <span>发布会旁白</span>
        <strong>{scene.subtitle}</strong>
        <div className="voiceover-progress">
          <b style={{width: `${Math.round(progress * 100)}%`}} />
        </div>
      </div>
      <div className="bullet-grid">
        {scene.bullets.map((bullet) => (
          <div className="bullet-card" key={`${scene.id}-${bullet}`}>
            <span />
            <strong>{bullet}</strong>
          </div>
        ))}
      </div>
      <div className="source-grid">
        <span>源码锚点</span>
        <div>
          {scene.sourceRefs.slice(0, 6).map((source) => (
            <code key={`${scene.id}-${source}`}>{source}</code>
          ))}
        </div>
      </div>
      <div className="scene-list">
        {neighborScenes.map((item) => (
          <div
            className={`scene-list-item ${item.id === scene.id ? "active" : ""}`}
            key={item.id}
          >
            <span>{String(item.chapter).padStart(2, "0")}</span>
            <strong>{item.eyebrow}</strong>
          </div>
        ))}
      </div>
      <div className="capability-wall">
        {scene.capabilities.map((capability) => (
          <span key={`${scene.id}-${capability}`}>{capability}</span>
        ))}
      </div>
      <div className="slogan-strip">
        <span>{androidDemoState.productName}</span>
        <strong>{androidDemoState.slogan}</strong>
      </div>
    </aside>
  );
}
