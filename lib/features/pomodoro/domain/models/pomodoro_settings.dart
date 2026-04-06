import 'package:project_lumen/features/settings/domain/models/app_settings.dart';

class PomodoroSettings {
  const PomodoroSettings({
    required this.workDuration,
    required this.shortBreakDuration,
    required this.longBreakDuration,
    required this.interactiveMode,
  });

  final Duration workDuration;
  final Duration shortBreakDuration;
  final Duration longBreakDuration;
  final bool interactiveMode;

  factory PomodoroSettings.fromAppSettings(AppSettings settings) {
    return PomodoroSettings(
      workDuration: settings.pomodoroWorkDuration,
      shortBreakDuration: settings.pomodoroShortBreakDuration,
      longBreakDuration: settings.pomodoroLongBreakDuration,
      interactiveMode: settings.pomodoroInteractiveMode,
    );
  }
}
