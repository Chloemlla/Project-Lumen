# Project Lumen 文档站主页说明

本文档单独说明 VitePress 首页的定位、结构和维护方式，避免首页内容和普通研发文档混在一起。

## 主页定位

首页不是营销落地页，也不是所有文档的复制粘贴。它承担三个职责：

- 让新读者在第一屏理解 Project Lumen 是 Android 护眼、专注、统计和远端能力的组合项目。
- 把已有长文档拆成清晰入口，帮助读者从现状、路线图、工程复现和研究材料中选择阅读路径。
- 为后续 GitHub Pages 发布提供稳定的文档门户。

## 首页结构

当前首页由四部分组成：

| 区块 | 作用 | 维护位置 |
| --- | --- | --- |
| Hero | 展示项目名、项目定位、图标和三个主入口 | `docs/index.md` |
| Feature cards | 用六张卡片承接产品、路线、工程、研究、日志和文档站说明 | `docs/index.md` |
| 文档入口地图 | 用三个面向任务的入口解释阅读顺序 | `docs/index.md` |
| 当前文档覆盖 | 用轻量指标说明文档范围 | `docs/index.md` |

## 视觉设计

文档站使用 restrained product-doc 风格：

- 主色使用青绿色，对应护眼与健康主题。
- 辅助色使用暖橙和珊瑚色，用于区分提醒、发布和路线图语义。
- 首页使用仓库已有项目图标作为主要视觉资产。
- 卡片圆角保持在 8px，避免文档站变成装饰性卡片堆叠。
- Markdown 表格、引用、代码块和导航统一由 `docs/.vitepress/theme/styles.css` 管理。

## 导航设计

导航和侧边栏在 `docs/.vitepress/config.ts` 中维护：

- `产品与商业化`：客户端能力、商业化路线和项目推进草稿。
- `工程与系统`：系统更新策略、Shizuku 原生护眼方案和配色参考。
- `研究与记录`：科创报告、每日开发日志和功能落地记录。

新增文档时，应优先放到现有分组。只有当文档形成新的稳定主题时，才新增分组。

## 构建与验证

本站构建命令只在 GitHub Actions 中执行，本地不执行实际构建或测试命令。

相关文件：

- `docs/package.json`
- `.github/workflows/vitepress-docs.yml`

GitHub workflow 会安装 `docs/` 内的 Node 依赖，运行 VitePress 构建，并上传静态站点产物。推送到默认分支时，workflow 还会发布到 GitHub Pages。

## 维护规则

- 首页只放入口、摘要和阅读路径，不堆完整需求。
- 长内容继续放在独立 Markdown 文档中。
- 新增首页卡片时，同步更新本说明文档的结构表。
- 调整主题色时，在 `styles.css` 的 CSS 变量中集中修改。
- 如果修改 GitHub Pages base path，同步检查 `docs/.vitepress/config.ts` 中的 `base` 配置。
