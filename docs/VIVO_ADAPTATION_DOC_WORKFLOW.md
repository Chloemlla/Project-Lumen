# Vivo 适配文档拉取与对照流程

这套流程把「打开 vivo 文档站 → 提取正文 → 对照仓库」固化成可复用脚本，避免每次都手动拆 SPA。

## 背景

vivo 开放平台文档页：

- `https://dev.vivo.com.cn/documentCenter/doc/<id>`

是前端 SPA。直接抓 HTML 只能拿到壳页面，正文在接口里：

```http
GET https://dev.vivo.com.cn/webapi/doc/info?id=<id>
Referer: https://dev.vivo.com.cn/documentCenter/doc/<id>
```

返回 JSON：

- `data.title`
- `data.content`（HTML）
- `data.versionCode`
- `data.updateTime`
- `data.breadCrumbs`

## 一键脚本

脚本路径：

- [`scripts/fetch_vivo_adaptation_doc.py`](../scripts/fetch_vivo_adaptation_doc.py)

### 基本用法

在仓库根目录执行：

```bash
python scripts/fetch_vivo_adaptation_doc.py 832
python scripts/fetch_vivo_adaptation_doc.py 1010
python scripts/fetch_vivo_adaptation_doc.py 797 832 1010 --scan-repo
```

Windows PowerShell 同样可用：

```powershell
python scripts/fetch_vivo_adaptation_doc.py 797 832 1010 --scan-repo
```

### 常用参数

| 参数 | 说明 |
|---|---|
| `doc_ids` | 一个或多个文档 ID，例如 `832`、`1010` |
| `--scan-repo` | 扫描本地源码中的常见适配关键词 |
| `--out-dir` | 输出目录，默认 `docs/vivo-adaptation` |
| `--keyword X` | 额外关键词，可重复 |
| `--timeout 30` | HTTP 超时秒数 |

### 输出结构

默认输出到 `docs/vivo-adaptation/`：

```text
docs/vivo-adaptation/
  REVIEW.md
  832-android-16/
    raw.json
    content.html
    content.txt
    headings.txt
  1010-android-17/
    raw.json
    content.html
    content.txt
    headings.txt
```

目录名优先使用 ASCII（`{id}-{english-slug}`），避免 Windows 控制台对中文路径显示乱码；正文文件本身仍是 UTF-8 中文。

含义：

- `raw.json`：元数据（标题、版本、更新时间、来源 URL）
- `content.html`：原始 HTML
- `content.txt`：清洗后的纯文本，方便全文检索
- `headings.txt`：自动提取的章节标题
- `REVIEW.md`：本次拉取汇总 +（可选）仓库关键词命中

## 推荐对照步骤

1. **拉文档**
   ```bash
   python scripts/fetch_vivo_adaptation_doc.py 797 832 1010 --scan-repo
   ```
2. **读 `headings.txt` / `content.txt`**
   先标高优先级章节，再下钻细节。
3. **看 `REVIEW.md` 的关键词命中**
   用命中行快速跳到 Manifest、BackHandler、Alarm、Share Intent、网络配置等位置。
4. **对照已有适配笔记**
   - [`ANDROID_16_VIVO_ADAPTATION.md`](./ANDROID_16_VIVO_ADAPTATION.md)
   - [`ANDROID_17_VIVO_ADAPTATION.md`](./ANDROID_17_VIVO_ADAPTATION.md)
5. **只改对本产品真正有影响的项**
   不相关项（如健康传感器、NPU、局域网发现）记录“不适用”即可。

## 默认会扫描的关键词

脚本内置一组对本仓库有用的词，例如：

- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation`
- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed`
- 定时：`scheduleAtFixedRate`、`ScheduledExecutorService`
- 分享/URI：`ACTION_SEND`、`FLAG_GRANT_READ_URI_PERMISSION`、`FileProvider`
- 网络：`usesCleartextTraffic`、`networkSecurityConfig`、`ACCESS_LOCAL_NETWORK`
- 音频/FGS：`mediaPlayback`、`USAGE_NOTIFICATION`、`FOREGROUND_SERVICE`

可用 `--keyword` 追加项目特有词。

## 已知限制

- 依赖 vivo 当前 `webapi/doc/info` 接口形态；若站点改版，需要同步更新脚本 URL/字段。
- 标题提取基于纯文本启发式，复杂表格/嵌套标题可能不完整，仍以 `content.txt` 为准。
- 关键词扫描只做“跳转辅助”，不是合规结论。

## 与现有文档的关系

- 本文件：描述**怎么拉、怎么对照**
- `ANDROID_14_VIVO_ADAPTATION.md` / `ANDROID_15_VIVO_ADAPTATION.md` / `ANDROID_16_VIVO_ADAPTATION.md` / `ANDROID_17_VIVO_ADAPTATION.md`：记录**对本项目的适配决策与结果**
- `docs/vivo-adaptation/`：脚本生成的**原始材料包**，可按需要提交或仅本地使用

如果要把某次拉取结果入库，建议至少提交：

1. 更新后的适配笔记
2. 必要代码改动
3. （可选）`docs/vivo-adaptation/REVIEW.md` 与对应 `content.txt`

## 控制台显示说明

PowerShell 默认代码页可能导致**控制台打印**中文标题乱码，但写入的 `content.txt` / `REVIEW.md` 使用 UTF-8，用编辑器打开是正常的。
