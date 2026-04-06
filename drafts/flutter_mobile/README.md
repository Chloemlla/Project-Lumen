# Flutter Mobile Draft

这是一套独立于当前 C# 项目的 Flutter 草案代码。

用途：

- 提供 `Drift` 建表草案
- 提供 `ReminderController / PomodoroController` 状态机骨架

假设：

- Flutter 包名为 `project_eye_mobile`
- 使用 `drift` 和 `riverpod`
- 这些文件需要复制进真正的 Flutter 工程后再执行代码生成

建议依赖：

```yaml
dependencies:
  drift:
  riverpod:

dev_dependencies:
  build_runner:
  drift_dev:
```

