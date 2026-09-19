# Project Lumen 用户体验全面优化 — 需求与验收清单（2026-09-19）

> 来源：对 `app/` Compose 界面的五路并行只读审查（首屏/主页、首启引导、设置/隐私、功能页、跨切面设计系统与无障碍）。
> 结论：本仓 UI 已相当成熟（0 处硬编码颜色、约 533 处 `stringResource`、已接入动态取色、已有空状态与 48dp 触达）。因此本轮只做**增量、非破坏性**的体验打磨，不做重构、不改业务/引擎逻辑、不新增功能。
>
> 硬约束（源自 `CLAUDE.md` / CI）：
> - **禁止本地构建/测试**，正确性以 GitHub Actions 为准（`unit-tests` / `android-lint` / `assemble-release` 含模拟器基线档案 → 启动崩溃会挂 CI）。
> - **新增字符串必须同时写入 `values/strings.xml` 与 `values-zh/strings.xml`**（lint 的 `MissingTranslation` 是致命项）。
> - 触觉类型只用普遍可用的 `HapticFeedbackType.LongPress` / `TextHandleMove`，不用可能超出本仓 Compose 版本的新常量。
> - 改动最小化、只碰表现层；引擎/状态/持久化/首启门控逻辑不动。
>
> 图例：优先级 P1（高价值）/ P2（中）/ P3（次要，本轮可暂缓）。每条完成后勾选并注明落地 commit。

## A. 触觉反馈（Haptics）

- [x] **R1 (P1)** 新增共享触觉助手 `ProjectLumenHaptics.kt`：`hapticClick(type, onClick)` 包装点击，先触觉后执行。文件：新建 `app/.../app/ProjectLumenHaptics.kt`。验收：编译通过，供以下各条复用。（d43a4e3，CI 全绿）
- [x] **R2 (P1)** 主页核心 CTA 加触觉（开始/暂停/继续/停止/休息/跳过/番茄）。文件：`ProjectLumenMainScreens.kt`。验收：各主按钮 onClick 经 `hapticClick`，不改传入 VM 的 lambda。（d43a4e3）
- [x] **R3 (P1)** 引导页 下一步/上一步用 `TextHandleMove`、完成用 `LongPress`。文件：`ProjectLumenOnboardingScreen.kt`。（d43a4e3）
- [x] **R4 (P2)** 夸克「我已完成签到」/ 撤销、模板选中 加触觉。文件：`ProjectLumenQuarkKeeperScreens.kt`、`ProjectLumenTemplateScreens.kt`。（d43a4e3）
- [x] **R5 (P2)** 隐私中心开关补触觉，与全局 `SwitchRow` 行为一致。文件：`ProjectLumenSettingsPrivacyCenter.kt`。（d43a4e3）

## B. 无障碍（TalkBack / 语义）

- [x] **R6 (P1)** `SmallMetric` / `MetricRow` 合并语义（`mergeDescendants` + `contentDescription = "标签, 值"`），一处修复全站指标行。文件：`ProjectLumenMetricsAndLayout.kt`。（d43a4e3）
- [x] **R7 (P1)** `UsageAppRow` 行合并语义，进度条移出无障碍树（用时已朗读即可）。文件：`ProjectLumenDeviceInsightsCard.kt`。（d43a4e3）
- [x] **R8 (P2)** `RiskScoreHeader` 分数圈/标签/说明合并为一个语义节点。文件：`ProjectLumenEyeCareInsights.kt`。（d43a4e3）
- [x] **R9 (P2)** `GoalLine` / `TimerCard` 进度条补 `mergeDescendants`，让读屏朗读“3/8 / 剩余时间”而非静默。文件：`ProjectLumenStatsAndTimerCards.kt`。（d43a4e3）
- [x] **R10 (P2)** 隐私就绪度进度条随 `MetricRow` 合并（进度条本身 `clearAndSetSemantics`，不再产生空节点）。文件：`ProjectLumenSettingsPrivacyCenter.kt`。（d43a4e3）
- [x] **R11 (P2)** 引导页进度条补进度语义 / 步骤描述。文件：`ProjectLumenOnboardingScreen.kt`（并入 R3）。（d43a4e3）
- [x] **R12 (P3)** 更新说明列表的裸「•」字形移出朗读（合并语义）。文件：`ProjectLumenBuildUpdateNotesScreen.kt`。（d43a4e3）

## C. 布局 / 状态 / 微文案

- [x] **R13 (P1)** 修复备份导入错误渲染在 `LumenPage` 之外（脱离滚动容器、可能盖住工具栏）。将 `backupImportError?.let{…}` 移入 `LumenPage` 的数据分区。文件：`ProjectLumenSettingsScreen.kt`。（d43a4e3）
- [x] **R14 (P2)** 主页 `TodayStatsCard` 首日全 0 时给一行空状态提示，而非六个「0」。新增字符串 `home_today_stats_empty_hint`。文件：`ProjectLumenStatsAndTimerCards.kt`。（d43a4e3）
- [x] **R15 (P2)** 数据分区（导出/导入）上方补一行说明文案（备份包含什么、导入会覆盖）。新增字符串 `settings_data_backup_hint`。文件：`ProjectLumenSettingsScreen.kt`。（d43a4e3）
- [x] **R16 (P2)** 日程时间选择器由旧版 `android.app.TimePickerDialog` 换成 Material3 `TimePicker`（与其下方 Material3 日期选择器在暗色/动态取色下一致）。文件：`ProjectLumenScheduleScreens.kt`。（d43a4e3）
- [x] **R17 (P3)** 法律中心列表分隔线内缩到与文字对齐（不再从图标下方满宽穿过）。文件：`ProjectLumenLegalCenterScreen.kt`。（d43a4e3）
- [x] **R18 (P3)** 引导页进度条切换用 `animateFloatAsState` 平滑过渡。文件：`ProjectLumenOnboardingScreen.kt`（并入 R3）。（d43a4e3）

## D. 补充与暂缓

- [x] **R21 (P3)** 计时脉冲动画尊重系统「减少动态效果」(`ANIMATOR_DURATION_SCALE`)：`ANIMATOR_DURATION_SCALE == 0` 时静置计时圆环不再脉动。文件：`ProjectLumenStatsAndTimerCards.kt`。（第二批）
- [x] **R20 (P3)** 无需改动 — 共享可折叠件 `SettingsSectionContent` 已同时具备 `animateContentSize`（卡片）与 `AnimatedVisibility`（内容淡入滑入），本已达标。
- [ ] **R19 (P3，暂缓)** 引入 `LumenSpacing` 间距标度对象（仅新增、机会性采用，禁批量重写）。当前无硬编码颜色、间距散布也不影响体验，收益低、采用面广，按方法论「改动最小化」不做批量扫改。
- [ ] **R22 (P3，需产品决策)** 主页统计区间「30 天」与「本月」（窗口 30 vs 31）。两者文案本已是不同字符串；删掉「30 天」这枚 chip 会移除一个用户可选区间，属功能改动而非纯文案打磨，不在本轮擅自删除，留待产品确认。

## 落地与验证

1. 需求文档先落地并推送（本文件）。
2. 共享件由协调者亲自改：`ProjectLumenHaptics.kt`（新建）、`ProjectLumenMetricsAndLayout.kt`、两份 `strings.xml`。
3. 各屏文件由互不相交的并行子代理落地，引用协调者预置的 `hapticClick` 与 `R.string.*`。
4. 分批 commit（Conventional Commits，GPG 可省）→ 推 `main` → 轮询 CI check-runs 至全绿 → 逐条勾选并注明 commit。
