# Seal UI/UX 优化（基座 + 设置页）

## Goal

参考 Seal 的 Material 3 UI/UX，优化 Project Lumen 的设计基座与设置页 chrome，让界面更柔和、更现代，同时保持现有功能与信息架构不变。

## What I already know

* 用户确认 MVP 范围：**基座 + 设置页改版**
* Seal 可借鉴点：Shapes 8/12/16/20/28、`surfaceContainerLow` 软表面、Preference 行（图标 chip）、LargeTopAppBar 折叠、无硬边框重卡片
* Lumen 现状：8dp 卡片 + elevation + border 过重；设置行再次描边形成双重盒子；顶栏为纯色 primary 自定义折叠条
* 动态色 / 模板色板路径需保留
* 本地禁止完整编译测试，验证走 GitHub workflow；完成后需 commit + push

## Requirements

1. **Theme 基座**
   * 提供 Seal 风格 Material `Shapes`
   * 卡片默认改为柔和 surface 容器：更大圆角、低/零 elevation、默认无硬边框
2. **共享组件**
   * `SwitchRow` / `NumberSlider` / 设置行改为 Seal Preference 风格（软底、图标 chip、去硬描边）
   * `SettingsSection` / `ActionCard` / `PageIntro` / metric 表面同步柔化
   * 底部导航改为更轻的 surface 风格
3. **设置页 chrome**
   * 顶栏改为 surface 层级 Material3 AppBar；二级页使用 `LargeTopAppBar` + collapse scroll behavior
   * dock / bottom-nav 主页面使用 compact `TopAppBar`，避免大折叠条在首屏留白
   * 设置页去掉厚重 intro 盒子感，更贴近 Preference 页面层次
   * 保留设置折叠分组、锚点滚动、权限引导等行为
4. **兼容**
   * 不改路由 / 设置键 / 计时与权限逻辑
   * 保留 dynamic color 与模板配色

## Acceptance Criteria

* [x] `ProjectLumenTheme` 暴露 Seal-like shapes
* [x] 共享卡片/设置行默认不再是“描边+抬升”双重盒子
* [x] 顶栏使用 surface 层级：二级页 LargeTopAppBar 折叠，dock 页 compact TopAppBar
* [x] 设置页视觉层次更接近 Seal Preference，功能行为不变
* [x] 设计 token 默认值与页面间距同步微调

## Definition of Done

* [x] 代码改动完成并自检关键路径
* [x] 不在本机跑完整 build/test（按 AGENTS.md）
* [x] commit + push
* [x] 任务文档（prd/research）与实现一致

## Technical Approach

* 新增 `ui/theme/Shape.kt`，接入 `ProjectLumenTheme`
* 收敛 `LumenCardShape` / `lumenCardColors` / elevation / border 默认
* 改造 `LumenTopBar` 为 Material3 LargeTopAppBar，并在 Scaffold 接入 nestedScroll
* 保留 `LocalLumenPageScrollState` 以兼容设置页锚点滚动
* 重点改：`ProjectLumenAppConstants`、`ProjectLumenFormControls`、`ProjectLumenSharedComponents`、`ProjectLumenMetricsAndLayout`、`ProjectLumenApp`、`ProjectLumenSettingsScreen`、`design/lumen-ui-tokens.json`

## Decision (ADR-lite)

**Context**: 需要在可控风险下明显提升 Lumen 观感，同时避免 dock 页大折叠顶栏造成首屏空带。  
**Decision**: 采用基座 + 设置页 chrome，不重做导航 IA；共享表面全面柔化，顶栏按 destination 分层。  
**Consequences**: 全站共享表面立刻受益；dock 页更紧凑；onboarding / developer debug / product demo 等局部硬描边可后续跟进。

### Final chrome decision

* Shared cards / preference rows: `surfaceContainerLow` + larger radii + zero elevation + no default border
* Dock destinations (`showInBottomNav=true`, including Settings): compact `TopAppBar` + pinned scroll
* Secondary destinations: `LargeTopAppBar` + `exitUntilCollapsedScrollBehavior`
* Behavior preserved: section expand/collapse memory, permission anchors, dynamic color / template palette

## Out of Scope

* Seal 功能移植
* 抽屉导航重构
* 全站逐页视觉重绘
* 引入 Kyant Monet 依赖
* Web/docs 改版

## Technical Notes

* 参考：`Seal/ui/theme/*`、`Seal/ui/component/PreferenceItems.kt`、`SettingItem.kt`、`BasePreferencePage.kt`
* Lumen：`app/src/main/java/com/projectlumen/app/ui/theme/*`、`app/ProjectLumen*.kt`
* tokens：`design/lumen-ui-tokens.json`（`app/build.gradle.kts` assets.srcDir）
* Research：`research/seal-ui-pattern-gap.md`
* Spec：`.trellis/spec/frontend/android-compose-surfaces.md`
