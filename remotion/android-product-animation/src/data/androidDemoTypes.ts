export type AccentName = "care" | "focus" | "sensor" | "data" | "advanced";

export type NavigationKey =
  | "HOME"
  | "BREAK"
  | "POMODORO"
  | "STATS"
  | "SETTINGS"
  | "TEMPLATES"
  | "ABOUT"
  | "DEVELOPER"
  | "WEB";

export type VisualMode =
  | "launch"
  | "onboarding"
  | "permission"
  | "home"
  | "reminder"
  | "service"
  | "pomodoro"
  | "distance"
  | "sensing"
  | "statistics"
  | "export"
  | "templates"
  | "settings"
  | "sound"
  | "appearance"
  | "backup"
  | "remote"
  | "shizuku"
  | "network"
  | "diagnostics"
  | "developer"
  | "crash"
  | "webview"
  | "open-api"
  | "data-flow";

export type PhoneMetric = {
  label: string;
  value: string;
  trend?: "up" | "down" | "steady";
};

export type SignalMetric = {
  label: string;
  value: string;
  progress: number;
};

export type SurfaceItem = {
  label: string;
  value: string;
  detail: string;
  progress?: number;
  status?: "done" | "on" | "warning" | "locked" | "sync";
};

export type PhoneScreenSurface = {
  mode: VisualMode;
  headline: string;
  subline: string;
  primaryAction: string;
  rows: SurfaceItem[];
  toggles: SurfaceItem[];
  timeline: string[];
  chart: number[];
  files: string[];
};

export type PhoneSceneState = {
  title: string;
  status: string;
  timer: string;
  progress: number;
  activeNav: NavigationKey;
  metrics: PhoneMetric[];
  surface: PhoneScreenSurface;
};

export type Spotlight = {
  label: string;
  value: string;
  caption: string;
};

export type DemoSceneDefinition = {
  id: string;
  accent: AccentName;
  eyebrow: string;
  chapterTitle: string;
  title: string;
  subtitle: string;
  voiceover: string;
  phone: PhoneSceneState;
  signals: SignalMetric[];
  capabilities: string[];
  bullets: string[];
  flowNodes: string[];
  sourceRefs: string[];
  spotlight: Spotlight;
};

export type DemoScene = DemoSceneDefinition & {
  chapter: number;
  totalChapters: number;
  start: number;
  duration: number;
};
