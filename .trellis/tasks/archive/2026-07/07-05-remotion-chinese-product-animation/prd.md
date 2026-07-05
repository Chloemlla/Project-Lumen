# Remotion 中文 Android 产品动画落地

## Goal

为 Project Lumen Android 客户端落地一套中文产品动画方案，并补齐可在 Android 实机展示的应用演示界面。动画应围绕护眼提醒、番茄钟、主动感知、统计、模板、Shizuku/云备份/API 等 Android 产品闭环，而不是展示后端或管理台。

## Requirements

* 新增 Remotion 中文产品动画实现，使用可控 mock 数据和组件重建 Android 产品关键界面。
* 新增 Android 实机演示界面，运行在现有 Kotlin/Compose 客户端中，用于展示稳定的产品状态和镜头入口。
* 不把现有 Android 客户端抽成 React Native；React Native 迁移只会增加重写成本，且 Remotion 仍主要渲染 React DOM/Web 组件，不能直接复用原生 Android 能力。
* 演示界面不连接真实后端、不依赖真实传感器数据，使用确定性演示状态。
* 中文文案优先，画面第一屏必须让观众看到手机和护眼核心。

## Acceptance Criteria

* [ ] 仓库中存在可维护的 Remotion 动画入口、场景组件和中文脚本数据。
* [ ] Android 客户端存在可打开的实机演示界面或演示入口，展示核心产品状态。
* [ ] Remotion 数据与 Android 演示数据在命名和叙事上保持一致。
* [ ] 不引入 React Native 重写路线。
* [ ] 构建和测试只通过 GitHub workflow 执行，本地不运行实际 build/test。

## Technical Approach

保留现有 Android Kotlin/Compose 客户端作为真实产品实现。新增一个 Compose 演示界面承载固定 demo state，适合实机录屏或人工演示。Remotion 侧新增独立 React/TypeScript 视频项目，用相同的 mock state、中文 copy、设计 token 和产品分镜重建可动画化界面。

## Decision (ADR-lite)

**Context**: 用户需要 Android 实机演示界面，同时希望完整落地 Remotion 中文动画。现有客户端是 Kotlin/Compose，并包含大量 Android 原生能力，例如前台服务、Shizuku、摄像头/光照、悬浮窗和通知。

**Decision**: 不将客户端抽成 React Native。采用“Compose 实机 demo + Remotion React 视频层”的双表面方案。

**Consequences**: 避免为了视频重写客户端；实机演示保持真实 Android 技术栈；Remotion 保持可控、可渲染、可剪辑。代价是视频 UI 与真实 Compose UI 是两套表现层，需要用共享 mock state 和设计 token 控制一致性。

## Out of Scope

* React Native 迁移或重写 Android 客户端。
* 真实传感器、人脸素材或真实后端 API 调用。
* 后端管理台、VitePress 文档站、UI token 调试工具的产品展示。

## Technical Notes

* 主要需求来源：`docs/REMOTION_ANDROID_PRODUCT_ANIMATION_GUIDE.md`。
* 现有客户端入口：`app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt`。
* 现有状态容器：`app/src/main/java/com/projectlumen/app/app/ProjectLumenUiState.kt`。
* 本任务禁止本地执行实际 build/test；验证应放到 GitHub workflow。
