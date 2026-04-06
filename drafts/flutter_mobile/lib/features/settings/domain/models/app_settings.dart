import 'package:project_eye_mobile/core/enums/reminder_action.dart';

class AppSettings {
  const AppSettings({
    required this.reminderEnabled,
    required this.warnIntervalMinutes,
    required this.restDurationSeconds,
    required this.preAlertEnabled,
    required this.preAlertSeconds,
    required this.preAlertDefaultAction,
    required this.preAlertTitle,
    required this.preAlertMessage,
    required this.askBeforeBreak,
    required this.disableSkip,
    required this.timeoutAutoBreak,
    required this.soundEnabled,
    required this.pomodoroEnabled,
    required this.pomodoroWorkMinutes,
    required this.pomodoroShortBreakMinutes,
    required this.pomodoroLongBreakMinutes,
    required this.pomodoroInteractiveMode,
    required this.pomodoroWorkStartSoundEnabled,
    required this.pomodoroWorkEndSoundEnabled,
  });

  final bool reminderEnabled;
  final int warnIntervalMinutes;
  final int restDurationSeconds;
  final bool preAlertEnabled;
  final int preAlertSeconds;
  final ReminderAction preAlertDefaultAction;
  final String preAlertTitle;
  final String preAlertMessage;
  final bool askBeforeBreak;
  final bool disableSkip;
  final bool timeoutAutoBreak;
  final bool soundEnabled;
  final bool pomodoroEnabled;
  final int pomodoroWorkMinutes;
  final int pomodoroShortBreakMinutes;
  final int pomodoroLongBreakMinutes;
  final bool pomodoroInteractiveMode;
  final bool pomodoroWorkStartSoundEnabled;
  final bool pomodoroWorkEndSoundEnabled;

  Duration get warnInterval => Duration(minutes: warnIntervalMinutes);

  Duration get restDuration => Duration(seconds: restDurationSeconds);

  Duration get preAlertDuration => Duration(seconds: preAlertSeconds);

  Duration get pomodoroWorkDuration => Duration(minutes: pomodoroWorkMinutes);

  Duration get pomodoroShortBreakDuration =>
      Duration(minutes: pomodoroShortBreakMinutes);

  Duration get pomodoroLongBreakDuration =>
      Duration(minutes: pomodoroLongBreakMinutes);
}

