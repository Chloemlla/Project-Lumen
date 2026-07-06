import type {CSSProperties} from "react";
import {
  Activity,
  BarChart3,
  Database,
  Eye,
  Radio,
  ShieldCheck,
  Sparkles,
  TimerReset,
} from "lucide-react";
import {
  AbsoluteFill,
  Img,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import {PhoneFrame} from "./components/PhoneFrame";
import {ScenePanel} from "./components/ScenePanel";
import {SensorOverlay} from "./components/SensorOverlay";
import {
  androidDemoState,
  demoScenes,
  getSceneAtFrame,
  sceneProgress,
  totalDurationInFrames,
} from "./data/androidDemoState";
import type {DemoScene} from "./data/androidDemoState";

const featureIcons = [
  {label: "护眼提醒", icon: Activity},
  {label: "番茄专注", icon: TimerReset},
  {label: "主动感知", icon: Eye},
  {label: "统计报告", icon: BarChart3},
  {label: "本地数据", icon: Database},
  {label: "高级保护", icon: ShieldCheck},
];

type StageAtmosphereStyle = CSSProperties & {
  "--light-shift": string;
  "--stage-light-opacity": string;
  "--reflection-opacity": string;
};

export function ProductAnimation() {
  const frame = useCurrentFrame();
  const videoConfig = useVideoConfig();
  const scene = getSceneAtFrame(frame);
  const progress = sceneProgress(scene, frame);
  const entrance = spring({
    frame: Math.max(frame - scene.start, 0),
    fps: videoConfig.fps,
    config: {
      damping: 24,
      stiffness: 120,
    },
  });
  const sceneFrame = Math.max(frame - scene.start, 0);
  const globalProgress = frame / Math.max(totalDurationInFrames - 1, 1);
  const chapterOpacity = interpolate(entrance, [0, 0.72, 1], [0, 0.86, 1]);
  const chapterExit = clamp01((progress - 0.92) / 0.08);
  const chapterIntroOpacity = 1 - clamp01((sceneFrame - 112) / 58);
  const chapterIntroScale = interpolate(clamp01(sceneFrame / 110), [0, 1], [0.985, 1]);
  const phoneY = interpolate(entrance, [0, 1], [70, 0]);
  const panelX = interpolate(entrance, [0, 1], [92, 0]);
  const heroY = interpolate(entrance, [0, 1], [42, 0]);
  const sceneDrift = Math.sin((sceneFrame + scene.chapter * 17) / 70) * 8;
  const phoneScale = interpolate(progress, [0, 0.54, 1], [0.988, 1.012, 1.002]);
  const phoneLean = interpolate(progress, [0, 0.5, 1], [-1.8, 0.8, 0]);
  const runtimeSeconds = Math.floor(frame / videoConfig.fps);
  const runtimeLabel = formatRuntime(runtimeSeconds);
  const atmosphereStyle: StageAtmosphereStyle = {
    "--light-shift": `${Math.round((progress - 0.5) * 92)}px`,
    "--stage-light-opacity": `${0.72 - chapterExit * 0.34}`,
    "--reflection-opacity": `${0.5 - chapterExit * 0.22}`,
    opacity: 0.62 + chapterOpacity * 0.38,
  };
  const phoneStyle: CSSProperties = {
    transform: `translate3d(0, ${phoneY + sceneDrift}px, 0) scale(${phoneScale}) rotateY(${phoneLean}deg)`,
    opacity: 1 - chapterExit * 0.28,
  };
  const panelStyle: CSSProperties = {
    transform: `translate3d(${panelX}px, 0, 0)`,
    opacity: chapterOpacity * (1 - chapterExit * 0.58),
  };
  const heroStyle: CSSProperties = {
    transform: `translate3d(0, ${heroY}px, 0)`,
    opacity: chapterOpacity * (1 - chapterExit * 0.4),
  };
  const chapterIntroStyle: CSSProperties = {
    opacity: chapterIntroOpacity,
    transform: `translate3d(0, ${interpolate(clamp01(sceneFrame / 120), [0, 1], [18, 0])}px, 0) scale(${chapterIntroScale})`,
  };

  return (
    <AbsoluteFill className={`composition accent-${scene.accent} mode-${scene.phone.surface.mode}`}>
      <div className="keynote-backdrop" />
      <div className="stage-light-bars" style={atmosphereStyle} />
      <div className="background-grid" />
      <div className="floor-reflection" style={atmosphereStyle} />
      <div className="cinema-frame" aria-hidden="true" />
      <ChapterIntro scene={scene} style={chapterIntroStyle} />
      <div className="brand-lockup">
        <Img className="brand-icon" src={staticFile("lumen-icon.png")} />
        <div>
          <strong>{androidDemoState.productName}</strong>
          <span>{androidDemoState.slogan}</span>
        </div>
      </div>
      <div className="runtime-lockup">
        <span>{runtimeLabel}</span>
        <strong>{scene.chapterTitle}</strong>
      </div>
      <div className="feature-orbit">
        {featureIcons.map(({label, icon: Icon}) => (
          <div className="feature-chip" key={label}>
            <Icon size={26} strokeWidth={2.4} />
            <span>{label}</span>
          </div>
        ))}
      </div>
      <main className="stage">
        <section className="hero-stage" style={heroStyle}>
          <span className="chapter-kicker">
            {String(scene.chapter).padStart(2, "0")} / {String(scene.totalChapters).padStart(2, "0")}
          </span>
          <h1>{scene.title}</h1>
          <p>{scene.subtitle}</p>
          <div className="spotlight-stat">
            <span>{scene.spotlight.label}</span>
            <strong>{scene.spotlight.value}</strong>
            <em>{scene.spotlight.caption}</em>
          </div>
        </section>
        <div className="phone-stage" style={phoneStyle}>
          <PhoneFrame scene={scene} progress={progress} />
          <SensorOverlay scene={scene} />
        </div>
        <div className="panel-stage" style={panelStyle}>
          <ScenePanel scene={scene} progress={progress} />
        </div>
      </main>
      <DataFlowRibbon scene={scene} progress={progress} />
      <ChapterRail scene={scene} />
      <div className="global-progress">
        <div className="global-progress-fill" style={{width: `${Math.round(globalProgress * 100)}%`}} />
      </div>
      <div className="closing-mark">
        <Sparkles size={24} strokeWidth={2.2} />
        <span>{androidDemoState.keynoteLine}</span>
      </div>
    </AbsoluteFill>
  );
}

function formatRuntime(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function clamp01(value: number): number {
  return Math.min(Math.max(value, 0), 1);
}

function ChapterIntro({scene, style}: {scene: DemoScene; style: CSSProperties}) {
  return (
    <section className="chapter-intro" style={style} aria-label={scene.chapterTitle}>
      <span>Chapter {String(scene.chapter).padStart(2, "0")}</span>
      <strong>{scene.eyebrow}</strong>
      <em>{scene.chapterTitle}</em>
    </section>
  );
}

function ChapterRail({scene}: {scene: DemoScene}) {
  const railStyle: CSSProperties = {
    gridTemplateColumns: `repeat(${demoScenes.length}, minmax(0, 1fr))`,
  };

  return (
    <nav className="chapter-rail" style={railStyle} aria-label="Project Lumen keynote chapters">
      {demoScenes.map((item) => (
        <div
          className={`chapter-dot ${item.id === scene.id ? "active" : ""} accent-${item.accent}`}
          key={item.id}
        >
          <span>{String(item.chapter).padStart(2, "0")}</span>
          <strong>{item.eyebrow}</strong>
        </div>
      ))}
    </nav>
  );
}

function DataFlowRibbon({scene, progress}: {scene: DemoScene; progress: number}) {
  const activeNodeCount = Math.max(1, Math.ceil(progress * scene.flowNodes.length));

  return (
    <section className="data-flow-ribbon" aria-label="Android data flow">
      <div className="ribbon-head">
        <Radio size={22} strokeWidth={2.3} />
        <span>本章数据流</span>
      </div>
      <div className="flow-node-list">
        {scene.flowNodes.map((node, index) => (
          <div className={`flow-node ${index < activeNodeCount ? "active" : ""}`} key={`${scene.id}-${node}`}>
            <span>{node}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
