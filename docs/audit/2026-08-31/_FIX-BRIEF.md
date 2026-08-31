# Project-Lumen 缺陷修复纲要（所有修复 agent 必读）

仓库：`F:\Repositories\GitHub\Project-Lumen`（Windows，分支 `main`）
总清单：`docs/audit/2026-08-31/AUDIT-CHECKLIST.md`（222 条），你的详细依据在你自己那份 `G*.md` 报告里。

## 硬性纪律（违反即任务失败）

1. **禁止运行任何构建 / 测试 / lint**：不得执行 `gradle`、`gradlew`、`npm`、`npx`、`bun`、`kotlinc`、`cmake`。
   本机性能不足，**CI 是唯一裁判**。你的产出就是落盘的代码改动。
2. **禁止 `git commit` / `git push` / `git add`**。协调者统一提交。
3. **禁止调用 `find`**（尤其 Git 自带的 `find.exe`）。找文件用 `fd`，搜内容用 `rg`，或用 Glob / Grep 工具。
4. **只准改分配给你的文件组**。别组的文件即使发现问题也**只读**，写进你的收尾报告让协调者转交。
5. **`rg` 在本机偶发会把标识符输出成乱码/单字符**。凡是要照抄的符号名，用 Read 工具或 `sed -n 'N,Mp'` 复核，
   **绝不能**根据 `rg` 的输出去改名字。
6. 不写新的文档文件、不写 `.md`（除了在收尾消息里汇报）。不加无意义注释：
   注释只写"为什么"，且一行以内；不要写"这里修复了 GXX-NN"这类会腐坏的注释。

## 修复范围与优先级

按 **P0 → P1 → P2** 顺序做你组里的每一条。原则：

- **有真实触发场景就修**。改动要最小、可评审，不要顺手重构无关代码、不要引入新抽象。
- **需要产品/UX 决策的**（例如"是否保留某功能"、"默认值改成多少要产品定"）：不要擅自改，跳过并在收尾报告里说明。
- **会改动公开 API / 跨组文件签名的**：不要改，报给协调者。
- 报告里标 `⚠需确认` 的：如果修复本身是纯加固（例如补 keep 规则、补 `close()`、补超时），直接修；
  如果修复会改变用户可见行为且证据不足，跳过并说明。

## 已由协调者完成、**不要再动**的部分

`core/security/DeviceSecurityGate.kt` 的门禁语义已重写（G01-01 / G06-02 已修）：

- `State` 新增 `DEGRADED`；只有 `rooted` 或原生完整性失败才 `BLOCKED`
- `isServiceAllowed()` = `state != BLOCKED`（本地功能在扫描未决时照常放行）
- 新增 `isFullyTrusted()`（= `ALLOWED`），`requireBackendAllowed` 改用它
- 新增 `suspend awaitDecision()`；`startStartupScan` 先 `quickScan()` 再 `fullScan()`，且完成后可重扫
- `ProjectLumenApplication.startTimerService()` 里 `TimerReconciliationWorker.enqueue` 已提到门禁检查之前

**不要回退这套设计**，也不要把 `isServiceAllowed()` 改回 `== ALLOWED`。

## 编译自检（不许跑构建，所以必须手工核）

改完每个文件后自查：

- 花括号平衡：`python -c "s=open('路径','r',encoding='utf-8').read(); print(s.count('{')==s.count('}'))"`
  （注意：字符串/正则字面量里的花括号会造成假阳性，不平衡时要人工确认）
- 新用的类型/函数**补 import**；删掉的用法要把不再使用的 import 一起删（未使用 import 会被 lint 拦）
- 改了函数签名 → `rg` 出**所有**调用点同步改（含命名参数写法）
- `when` 分支穷尽；新增 enum 值时检查是否有别处的穷尽 `when`
- **不要在静态 / 顶层初始化里写 `Handler(Looper.getMainLooper())`**，纯 JVM 单测会在类加载时 NPE；用 `by lazy`
- 单测里有对源码文本做断言的用例（例如断言某文件包含某字符串），改代码前先 `rg` 一下 `app/src/test`，
  别把这类断言改崩

## 收尾报告（发给协调者的最后一条消息）

逐条列：`缺陷ID → 已修 / 跳过(原因)`，并列出**你改过的所有文件路径**。
协调者会用 `git diff` 核实——报告说改了但磁盘上没有的，一律视为未完成。
