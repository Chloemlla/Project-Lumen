import type {
  DemoScene,
  DemoSceneDefinition,
  PhoneMetric,
  SignalMetric,
  SurfaceItem,
} from "./androidDemoTypes";

import {accountAdvancedScenes} from "./chapters/accountAdvancedScenes";
import {experienceScenes} from "./chapters/experienceScenes";
import {foundationScenes} from "./chapters/foundationScenes";
import {platformScenes} from "./chapters/platformScenes";
import {runtimeScenes} from "./chapters/runtimeScenes";

export type {
  AccentName,
  DemoScene,
  NavigationKey,
  PhoneMetric,
  PhoneSceneState,
  PhoneScreenSurface,
  SignalMetric,
  Spotlight,
  SurfaceItem,
  VisualMode,
} from "./androidDemoTypes";

export const chapterDurationInFrames = 1500;
export const minimumDurationInSeconds = 1200;

const sceneDefinitions = [
  ...foundationScenes,
  ...runtimeScenes,
  ...experienceScenes,
  ...accountAdvancedScenes,
  ...platformScenes,
] satisfies DemoSceneDefinition[];

export const demoScenes: DemoScene[] = sceneDefinitions.map((scene, index) => ({
  ...scene,
  chapter: index + 1,
  totalChapters: sceneDefinitions.length,
  start: index * chapterDurationInFrames,
  duration: chapterDurationInFrames,
}));

function resolveClosingScene(): DemoScene {
  const closingScene = demoScenes.find((scene) => scene.id === "open-api-data-flow");
  if (closingScene) {
    return closingScene;
  }

  const fallbackScene = demoScenes.find((scene) => scene.chapter === sceneDefinitions.length);
  if (fallbackScene) {
    return fallbackScene;
  }

  throw new Error("Project Lumen demo scenes must include at least one closing scene.");
}

const closingScene = resolveClosingScene();

export const totalDurationInFrames = demoScenes.reduce(
  (duration, scene) => Math.max(duration, scene.start + scene.duration),
  0,
);

export const totalDurationInSeconds = Math.floor(totalDurationInFrames / 30);

export const androidDemoState = {
  productName: "Project Lumen",
  slogan: "Android 上本地优先的智能护眼系统",
  keynoteLine: "把提醒、专注、主动感知和高级保护，收束成一个可控的护眼节奏。",
  navigation: ["首页", "休息", "番茄钟", "统计", "设置"],
  secondaryNavigation: ["翻译", "模板", "关于", "开发者", "WebView", "开放 API"],
  chapterDurationInFrames,
  totalDurationInFrames,
  totalDurationInSeconds,
  minimumDurationInSeconds,
};

export function getSceneAtFrame(frame: number): DemoScene {
  const scene = demoScenes.find(
    (candidate) =>
      frame >= candidate.start && frame < candidate.start + candidate.duration,
  );

  return scene ?? closingScene;
}

export function sceneProgress(scene: DemoScene, frame: number): number {
  const rawProgress = (frame - scene.start) / scene.duration;
  return Math.min(Math.max(rawProgress, 0), 1);
}

export function metricTrendLabel(metric: PhoneMetric): string {
  if (metric.trend === "up") {
    return "上升";
  }

  if (metric.trend === "down") {
    return "下降";
  }

  return "稳定";
}

export function signalLevel(signal: SignalMetric): "low" | "medium" | "high" {
  if (signal.progress >= 0.75) {
    return "high";
  }

  if (signal.progress >= 0.45) {
    return "medium";
  }

  return "low";
}

export function surfaceItemStatusLabel(item: SurfaceItem): string {
  if (item.status === "done") {
    return "完成";
  }

  if (item.status === "warning") {
    return "注意";
  }

  if (item.status === "locked") {
    return "锁定";
  }

  if (item.status === "sync") {
    return "同步";
  }

  return "开启";
}
