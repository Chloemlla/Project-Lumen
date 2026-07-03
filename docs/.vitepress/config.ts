import { defineConfig } from "vitepress";

const productDocs = [
  { text: "客户端现有功能", link: "/CLIENT_EXISTING_FEATURES" },
  { text: "商业化路线图", link: "/NEXT_STEPS_FEATURE_RECOMMENDATIONS" },
  { text: "项目推进草稿", link: "/PROJECT_LUMEN_PROJECT_ADVANCEMENT_DRAFT" },
];

const technicalDocs = [
  { text: "系统更新策略", link: "/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION" },
  { text: "Shizuku 原生护眼方案", link: "/docs" },
  { text: "配色参考", link: "/配色" },
];

const researchDocs = [
  { text: "科创研究报告", link: "/PROJECT_LUMEN_SCIENCE_INNOVATION_RESEARCH_REPORT" },
  { text: "每日开发日志", link: "/PROJECT_LUMEN_DAILY_DEVELOPMENT_LOG" },
  { text: "远端对接落地记录", link: "/TODO1_REMOTE_INTEGRATION_LANDING" },
  { text: "护眼洞察落地记录", link: "/TODO2_EYE_CARE_INSIGHTS_LANDING" },
];

const basePath = process.env.VITEPRESS_BASE ?? "/Project-Lumen/";

export default defineConfig({
  title: "Project Lumen",
  description: "Project Lumen 护眼、专注、统计和远端能力文档中心",
  lang: "zh-CN",
  base: basePath,
  cleanUrls: true,
  lastUpdated: true,
  markdown: {
    lineNumbers: true,
  },
  head: [
    ["link", { rel: "icon", href: `${basePath}lumen-icon.png` }],
    ["meta", { name: "theme-color", content: "#0f766e" }],
    ["meta", { property: "og:title", content: "Project Lumen 文档中心" }],
    [
      "meta",
      {
        property: "og:description",
        content: "面向 Project Lumen 产品、研发、商业化和系统能力的 VitePress 文档站。",
      },
    ],
  ],
  themeConfig: {
    logo: "/lumen-icon.png",
    siteTitle: "Project Lumen",
    outline: {
      level: [2, 3],
      label: "本页目录",
    },
    search: {
      provider: "local",
      options: {
        translations: {
          button: {
            buttonText: "搜索文档",
            buttonAriaLabel: "搜索文档",
          },
          modal: {
            noResultsText: "没有找到结果",
            resetButtonTitle: "清除查询",
            footer: {
              selectText: "选择",
              navigateText: "切换",
              closeText: "关闭",
            },
          },
        },
      },
    },
    nav: [
      { text: "首页", link: "/" },
      { text: "主页说明", link: "/homepage-guide" },
      { text: "产品能力", link: "/CLIENT_EXISTING_FEATURES" },
      { text: "路线图", link: "/NEXT_STEPS_FEATURE_RECOMMENDATIONS" },
      {
        text: "技术文档",
        items: technicalDocs,
      },
      {
        text: "研发记录",
        items: researchDocs,
      },
    ],
    sidebar: [
      {
        text: "文档站",
        collapsed: false,
        items: [
          { text: "首页", link: "/" },
          { text: "主页说明", link: "/homepage-guide" },
        ],
      },
      {
        text: "产品与商业化",
        collapsed: false,
        items: productDocs,
      },
      {
        text: "工程与系统",
        collapsed: false,
        items: technicalDocs,
      },
      {
        text: "研究与记录",
        collapsed: true,
        items: researchDocs,
      },
    ],
    socialLinks: [
      { icon: "github", link: "https://github.com/Chloemlla/Project-Lumen" },
    ],
    footer: {
      message: "Built with VitePress for Project Lumen.",
      copyright: "Copyright © 2026 Project Lumen",
    },
    docFooter: {
      prev: "上一页",
      next: "下一页",
    },
    lastUpdated: {
      text: "最后更新",
      formatOptions: {
        dateStyle: "medium",
        timeStyle: "short",
      },
    },
    darkModeSwitchLabel: "切换主题",
    sidebarMenuLabel: "文档导航",
    returnToTopLabel: "返回顶部",
    langMenuLabel: "切换语言",
  },
});
