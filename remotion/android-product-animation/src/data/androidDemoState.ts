export type AccentName = "care" | "focus" | "sensor" | "data" | "advanced";

export type PhoneMetric = {
  label: string;
  value: string;
};

export type SignalMetric = {
  label: string;
  value: string;
  progress: number;
};

export type PhoneSceneState = {
  title: string;
  status: string;
  timer: string;
  progress: number;
  metrics: PhoneMetric[];
};

export type DemoScene = {
  id: string;
  start: number;
  duration: number;
  accent: AccentName;
  eyebrow: string;
  title: string;
  subtitle: string;
  voiceover: string;
  phone: PhoneSceneState;
  signals: SignalMetric[];
  capabilities: string[];
};

export const androidDemoState = {
  productName: "Project Lumen",
  slogan: "Android 上本地优先的护眼系统",
  navigation: ["首页", "休息", "番茄钟", "统计", "设置"],
};

export const demoScenes: DemoScene[] = [
  {
    id: "opening",
    start: 0,
    duration: 180,
    accent: "care",
    eyebrow: "0-6 秒",
    title: "把屏幕时间变成护眼节奏",
    subtitle: "手机亮屏后直接进入 Project Lumen 的本地护眼闭环。",
    voiceover: "Project Lumen 把屏幕使用时间变成可感知、可调节的护眼节奏。",
    phone: {
      title: "专注健康",
      status: "就绪",
      timer: "20:00",
      progress: 0.12,
      metrics: [
        {label: "工作", value: "0 分钟"},
        {label: "休息", value: "0 分钟"},
        {label: "目标", value: "0/8"},
        {label: "提醒", value: "已开启"},
      ],
    },
    signals: [
      {label: "通知权限", value: "已就绪", progress: 1},
      {label: "统计记录", value: "开启", progress: 1},
      {label: "遮罩防护", value: "待触发", progress: 0.28},
    ],
    capabilities: ["本地优先", "Material Android", "中文产品片"],
  },
  {
    id: "setup",
    start: 180,
    duration: 240,
    accent: "care",
    eyebrow: "6-14 秒",
    title: "一键推荐配置",
    subtitle: "提醒、统计、预提醒、近距离、眨眼、低光和全屏休息同时点亮。",
    voiceover: "一键推荐配置，开启提醒、统计、监测、亮度和全屏休息。",
    phone: {
      title: "推荐护眼配置",
      status: "正在应用",
      timer: "5 项",
      progress: 0.86,
      metrics: [
        {label: "提醒", value: "20 分钟"},
        {label: "休息", value: "20 秒"},
        {label: "监测", value: "3 类"},
        {label: "目标", value: "8 次"},
      ],
    },
    signals: [
      {label: "距离检测", value: "已开启", progress: 1},
      {label: "眨眼检测", value: "已开启", progress: 1},
      {label: "低光监测", value: "已开启", progress: 1},
    ],
    capabilities: ["推荐设置", "设备指纹", "隐私默认本地"],
  },
  {
    id: "home",
    start: 420,
    duration: 360,
    accent: "data",
    eyebrow: "14-28 秒",
    title: "首页汇总今天、下一次休息和目标",
    subtitle: "状态卡、今日统计、目标进度和快捷操作保持在一个可扫视界面。",
    voiceover: "首页汇总今天、下一次休息和目标进度。",
    phone: {
      title: "今日护眼",
      status: "工作中",
      timer: "08:20",
      progress: 0.58,
      metrics: [
        {label: "工作", value: "96 分钟"},
        {label: "休息", value: "12 分钟"},
        {label: "完成", value: "6 次"},
        {label: "目标", value: "75%"},
      ],
    },
    signals: [
      {label: "连续工作", value: "18 分钟", progress: 0.76},
      {label: "跳过休息", value: "1 次", progress: 0.24},
      {label: "健康洞察", value: "2 条", progress: 0.62},
    ],
    capabilities: ["状态卡", "今日统计", "快捷操作"],
  },
  {
    id: "break-loop",
    start: 780,
    duration: 420,
    accent: "care",
    eyebrow: "28-43 秒",
    title: "从预提醒进入休息，再回到专注",
    subtitle: "工作计时、通知操作、全屏遮罩和休息模板形成完整状态机。",
    voiceover: "当连续工作过久，Lumen 从预提醒进入休息，再回到专注。",
    phone: {
      title: "该休息了",
      status: "休息中",
      timer: "00:20",
      progress: 0.68,
      metrics: [
        {label: "阶段", value: "RESTING"},
        {label: "模板", value: "柔和绿"},
        {label: "跳过", value: "禁用"},
        {label: "通知", value: "已同步"},
      ],
    },
    signals: [
      {label: "预提醒", value: "已触发", progress: 1},
      {label: "全屏遮罩", value: "开启", progress: 0.92},
      {label: "下一轮工作", value: "待恢复", progress: 0.34},
    ],
    capabilities: ["ReminderEngine", "通知操作", "休息模板"],
  },
  {
    id: "pomodoro",
    start: 1200,
    duration: 300,
    accent: "focus",
    eyebrow: "43-53 秒",
    title: "番茄钟与护眼提醒共享运行态",
    subtitle: "专注、短休和长休不会和护眼计时器互相抢占。",
    voiceover: "番茄钟与护眼提醒共享运行态，避免多个计时器互相冲突。",
    phone: {
      title: "番茄专注",
      status: "专注中",
      timer: "18:40",
      progress: 0.42,
      metrics: [
        {label: "周期", value: "3/4"},
        {label: "番茄", value: "3"},
        {label: "短休", value: "5 分钟"},
        {label: "长休", value: "15 分钟"},
      ],
    },
    signals: [
      {label: "主引擎", value: "POMODORO", progress: 1},
      {label: "护眼提醒", value: "等待", progress: 0.18},
      {label: "专注统计", value: "写入", progress: 0.72},
    ],
    capabilities: ["Focus", "Short Break", "Long Break"],
  },
  {
    id: "sensors",
    start: 1500,
    duration: 390,
    accent: "sensor",
    eyebrow: "53-66 秒",
    title: "摄像头、眨眼和环境光让保护更主动",
    subtitle: "抽象人脸框、距离倍率、睁眼概率和 lux 波形驱动风险提示。",
    voiceover: "摄像头、眨眼和环境光让保护更主动。",
    phone: {
      title: "主动护眼",
      status: "风险升高",
      timer: "165%",
      progress: 0.84,
      metrics: [
        {label: "距离", value: "165%"},
        {label: "眨眼", value: "1 次"},
        {label: "光照", value: "8 lux"},
        {label: "遮罩", value: "准备"},
      ],
    },
    signals: [
      {label: "距离倍率", value: "165%", progress: 0.84},
      {label: "睁眼概率", value: "偏低", progress: 0.42},
      {label: "环境光", value: "8 lux", progress: 0.18},
    ],
    capabilities: ["人脸框", "lux 波形", "Extra Dim"],
  },
  {
    id: "reports",
    start: 1890,
    duration: 300,
    accent: "data",
    eyebrow: "66-76 秒",
    title: "每次使用沉淀为趋势和报告",
    subtitle: "7 天和 30 天趋势、目标进度、CSV/PNG/PDF 导出在同一段落收束。",
    voiceover: "每次使用都会沉淀为趋势、目标和可导出的报告。",
    phone: {
      title: "统计趋势",
      status: "本周",
      timer: "7 天",
      progress: 0.74,
      metrics: [
        {label: "休息", value: "42 次"},
        {label: "专注", value: "14 次"},
        {label: "低光", value: "3 次"},
        {label: "导出", value: "PDF"},
      ],
    },
    signals: [
      {label: "7 天趋势", value: "上升", progress: 0.74},
      {label: "月度报告", value: "可导出", progress: 0.88},
      {label: "习惯建议", value: "已生成", progress: 0.66},
    ],
    capabilities: ["CSV", "PNG", "PDF"],
  },
  {
    id: "advanced",
    start: 2190,
    duration: 510,
    accent: "advanced",
    eyebrow: "76-90 秒",
    title: "模板、Shizuku、云备份和开放 API 收束",
    subtitle: "高级能力只点到为止，服务于 Android 本地护眼主线。",
    voiceover: "Shizuku、云备份、诊断和开放 API 支撑高级场景。",
    phone: {
      title: "高级保护",
      status: "已授权",
      timer: "PRO",
      progress: 0.92,
      metrics: [
        {label: "模板", value: "3 张"},
        {label: "Shizuku", value: "可用"},
        {label: "云备份", value: "已同步"},
        {label: "API", value: "已开放"},
      ],
    },
    signals: [
      {label: "原生护眼", value: "4200 K", progress: 0.82},
      {label: "云备份", value: "完成", progress: 1},
      {label: "开放 API", value: "签名校验", progress: 0.76},
    ],
    capabilities: ["模板个性化", "Shizuku", "云备份", "开放 API"],
  },
];

function resolveClosingScene(): DemoScene {
  const scene = demoScenes.find((candidate) => candidate.id === "advanced");
  if (scene) {
    return scene;
  }
  throw new Error("Project Lumen demo scenes must include an advanced closing scene.");
}

const lastScene = resolveClosingScene();

export const totalDurationInFrames = demoScenes.reduce(
  (duration, scene) => Math.max(duration, scene.start + scene.duration),
  0,
);

export function getSceneAtFrame(frame: number): DemoScene {
  return (
    demoScenes.find((scene) => frame >= scene.start && frame < scene.start + scene.duration) ??
    lastScene
  );
}

export function sceneProgress(scene: DemoScene, frame: number): number {
  return Math.min(Math.max((frame - scene.start) / scene.duration, 0), 1);
}
