import {Activity, BarChart3, Cloud, ShieldCheck, Sparkles} from "lucide-react";
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
  getSceneAtFrame,
  sceneProgress,
  totalDurationInFrames,
} from "./data/androidDemoState";

const featureIcons = [
  {label: "提醒", icon: Activity},
  {label: "统计", icon: BarChart3},
  {label: "Shizuku", icon: ShieldCheck},
  {label: "云备份", icon: Cloud},
];

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
  const globalProgress = frame / Math.max(totalDurationInFrames - 1, 1);
  const phoneY = interpolate(entrance, [0, 1], [64, 0]);
  const panelX = interpolate(entrance, [0, 1], [80, 0]);

  return (
    <AbsoluteFill className={`composition accent-${scene.accent}`}>
      <div className="background-grid" />
      <div className="brand-lockup">
        <Img className="brand-icon" src={staticFile("lumen-icon.png")} />
        <div>
          <strong>{androidDemoState.productName}</strong>
          <span>{androidDemoState.slogan}</span>
        </div>
      </div>
      <div className="feature-orbit" aria-hidden="true">
        {featureIcons.map(({label, icon: Icon}) => (
          <div className="feature-chip" key={label}>
            <Icon size={26} strokeWidth={2.4} />
            <span>{label}</span>
          </div>
        ))}
      </div>
      <main className="stage">
        <div className="phone-stage" style={{transform: `translateY(${phoneY}px)`}}>
          <PhoneFrame scene={scene} progress={progress} />
          <SensorOverlay scene={scene} />
        </div>
        <div className="panel-stage" style={{transform: `translateX(${panelX}px)`}}>
          <ScenePanel scene={scene} />
        </div>
      </main>
      <div className="global-progress">
        <div className="global-progress-fill" style={{width: `${Math.round(globalProgress * 100)}%`}} />
      </div>
      <div className="closing-mark">
        <Sparkles size={24} strokeWidth={2.2} />
        <span>中文 Android 产品动画</span>
      </div>
    </AbsoluteFill>
  );
}
