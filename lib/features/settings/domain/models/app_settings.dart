import 'package:project_lumen/core/enums/app_theme_mode.dart';
import 'package:project_lumen/core/enums/reminder_action.dart';

class AppSettings {
  const AppSettings({
    required this.id,
    required this.languageCode,
    required this.themeMode,
    required this.useAutoDarkWindow,
    required this.autoDarkStartMinute,
    required this.autoDarkEndMinute,
    required this.reminderEnabled,
    required this.warnIntervalMinutes,
    required this.restDurationSeconds,
    required this.statsEnabled,
    required this.soundEnabled,
    required this.restSoundPath,
    required this.preAlertEnabled,
    required this.preAlertSeconds,
    required this.preAlertDefaultAction,
    required this.preAlertTitle,
    required this.preAlertSubtitle,
    required this.preAlertMessage,
    required this.preAlertIconPath,
    required this.preAlertSoundEnabled,
    required this.askBeforeBreak,
    required this.disableSkip,
    required this.timeoutAutoBreak,
    required this.pomodoroEnabled,
    required this.pomodoroWorkMinutes,
    required this.pomodoroShortBreakMinutes,
    required this.pomodoroLongBreakMinutes,
    required this.pomodoroInteractiveMode,
    required this.pomodoroWorkStartSoundEnabled,
    required this.pomodoroWorkEndSoundEnabled,
    required this.pomodoroWorkStartSoundPath,
    required this.pomodoroWorkEndSoundPath,
    required this.activeTipTemplateId,
    required this.statsWorkImagePath,
    required this.statsRestImagePath,
    required this.statsSkipImagePath,
    required this.updatedAt,
  });

  final int id;
  final String languageCode;
  final AppThemeMode themeMode;
  final bool useAutoDarkWindow;
  final int autoDarkStartMinute;
  final int autoDarkEndMinute;
  final bool reminderEnabled;
  final int warnIntervalMinutes;
  final int restDurationSeconds;
  final bool statsEnabled;
  final bool soundEnabled;
  final String? restSoundPath;
  final bool preAlertEnabled;
  final int preAlertSeconds;
  final ReminderAction preAlertDefaultAction;
  final String preAlertTitle;
  final String preAlertSubtitle;
  final String preAlertMessage;
  final String? preAlertIconPath;
  final bool preAlertSoundEnabled;
  final bool askBeforeBreak;
  final bool disableSkip;
  final bool timeoutAutoBreak;
  final bool pomodoroEnabled;
  final int pomodoroWorkMinutes;
  final int pomodoroShortBreakMinutes;
  final int pomodoroLongBreakMinutes;
  final bool pomodoroInteractiveMode;
  final bool pomodoroWorkStartSoundEnabled;
  final bool pomodoroWorkEndSoundEnabled;
  final String? pomodoroWorkStartSoundPath;
  final String? pomodoroWorkEndSoundPath;
  final int? activeTipTemplateId;
  final String? statsWorkImagePath;
  final String? statsRestImagePath;
  final String? statsSkipImagePath;
  final DateTime updatedAt;

  factory AppSettings.defaults() {
    return AppSettings(
      id: 1,
      languageCode: 'zh',
      themeMode: AppThemeMode.system,
      useAutoDarkWindow: false,
      autoDarkStartMinute: 1080,
      autoDarkEndMinute: 360,
      reminderEnabled: true,
      warnIntervalMinutes: 20,
      restDurationSeconds: 20,
      statsEnabled: true,
      soundEnabled: true,
      restSoundPath: null,
      preAlertEnabled: true,
      preAlertSeconds: 15,
      preAlertDefaultAction: ReminderAction.startBreak,
      preAlertTitle: '即将进入休息',
      preAlertSubtitle: '请保存当前进度',
      preAlertMessage: '倒计时结束后建议开始短暂休息',
      preAlertIconPath: null,
      preAlertSoundEnabled: false,
      askBeforeBreak: true,
      disableSkip: false,
      timeoutAutoBreak: true,
      pomodoroEnabled: true,
      pomodoroWorkMinutes: 25,
      pomodoroShortBreakMinutes: 5,
      pomodoroLongBreakMinutes: 15,
      pomodoroInteractiveMode: true,
      pomodoroWorkStartSoundEnabled: false,
      pomodoroWorkEndSoundEnabled: false,
      pomodoroWorkStartSoundPath: null,
      pomodoroWorkEndSoundPath: null,
      activeTipTemplateId: 1,
      statsWorkImagePath: null,
      statsRestImagePath: null,
      statsSkipImagePath: null,
      updatedAt: DateTime.now(),
    );
  }

  Duration get warnInterval => Duration(minutes: warnIntervalMinutes);

  Duration get restDuration => Duration(seconds: restDurationSeconds);

  Duration get preAlertDuration => Duration(seconds: preAlertSeconds);

  Duration get pomodoroWorkDuration => Duration(minutes: pomodoroWorkMinutes);

  Duration get pomodoroShortBreakDuration =>
      Duration(minutes: pomodoroShortBreakMinutes);

  Duration get pomodoroLongBreakDuration =>
      Duration(minutes: pomodoroLongBreakMinutes);

  AppSettings copyWith({
    String? languageCode,
    AppThemeMode? themeMode,
    bool? reminderEnabled,
    int? warnIntervalMinutes,
    int? restDurationSeconds,
    bool? preAlertEnabled,
    int? preAlertSeconds,
    ReminderAction? preAlertDefaultAction,
    String? preAlertTitle,
    String? preAlertSubtitle,
    String? preAlertMessage,
    bool? askBeforeBreak,
    bool? disableSkip,
    bool? timeoutAutoBreak,
    bool? soundEnabled,
    bool? pomodoroEnabled,
    int? pomodoroWorkMinutes,
    int? pomodoroShortBreakMinutes,
    int? pomodoroLongBreakMinutes,
    bool? pomodoroInteractiveMode,
    bool? pomodoroWorkStartSoundEnabled,
    bool? pomodoroWorkEndSoundEnabled,
    int? activeTipTemplateId,
  }) {
    return AppSettings(
      id: id,
      languageCode: languageCode ?? this.languageCode,
      themeMode: themeMode ?? this.themeMode,
      useAutoDarkWindow: useAutoDarkWindow,
      autoDarkStartMinute: autoDarkStartMinute,
      autoDarkEndMinute: autoDarkEndMinute,
      reminderEnabled: reminderEnabled ?? this.reminderEnabled,
      warnIntervalMinutes: warnIntervalMinutes ?? this.warnIntervalMinutes,
      restDurationSeconds: restDurationSeconds ?? this.restDurationSeconds,
      statsEnabled: statsEnabled,
      soundEnabled: soundEnabled ?? this.soundEnabled,
      restSoundPath: restSoundPath,
      preAlertEnabled: preAlertEnabled ?? this.preAlertEnabled,
      preAlertSeconds: preAlertSeconds ?? this.preAlertSeconds,
      preAlertDefaultAction:
          preAlertDefaultAction ?? this.preAlertDefaultAction,
      preAlertTitle: preAlertTitle ?? this.preAlertTitle,
      preAlertSubtitle: preAlertSubtitle ?? this.preAlertSubtitle,
      preAlertMessage: preAlertMessage ?? this.preAlertMessage,
      preAlertIconPath: preAlertIconPath,
      preAlertSoundEnabled: preAlertSoundEnabled,
      askBeforeBreak: askBeforeBreak ?? this.askBeforeBreak,
      disableSkip: disableSkip ?? this.disableSkip,
      timeoutAutoBreak: timeoutAutoBreak ?? this.timeoutAutoBreak,
      pomodoroEnabled: pomodoroEnabled ?? this.pomodoroEnabled,
      pomodoroWorkMinutes: pomodoroWorkMinutes ?? this.pomodoroWorkMinutes,
      pomodoroShortBreakMinutes:
          pomodoroShortBreakMinutes ?? this.pomodoroShortBreakMinutes,
      pomodoroLongBreakMinutes:
          pomodoroLongBreakMinutes ?? this.pomodoroLongBreakMinutes,
      pomodoroInteractiveMode:
          pomodoroInteractiveMode ?? this.pomodoroInteractiveMode,
      pomodoroWorkStartSoundEnabled:
          pomodoroWorkStartSoundEnabled ?? this.pomodoroWorkStartSoundEnabled,
      pomodoroWorkEndSoundEnabled:
          pomodoroWorkEndSoundEnabled ?? this.pomodoroWorkEndSoundEnabled,
      pomodoroWorkStartSoundPath: pomodoroWorkStartSoundPath,
      pomodoroWorkEndSoundPath: pomodoroWorkEndSoundPath,
      activeTipTemplateId: activeTipTemplateId ?? this.activeTipTemplateId,
      statsWorkImagePath: statsWorkImagePath,
      statsRestImagePath: statsRestImagePath,
      statsSkipImagePath: statsSkipImagePath,
      updatedAt: DateTime.now(),
    );
  }

  Map<String, Object?> toMap() {
    return {
      'id': id,
      'language_code': languageCode,
      'theme_mode': themeMode.storageValue,
      'use_auto_dark_window': useAutoDarkWindow ? 1 : 0,
      'auto_dark_start_minute': autoDarkStartMinute,
      'auto_dark_end_minute': autoDarkEndMinute,
      'reminder_enabled': reminderEnabled ? 1 : 0,
      'warn_interval_minutes': warnIntervalMinutes,
      'rest_duration_seconds': restDurationSeconds,
      'stats_enabled': statsEnabled ? 1 : 0,
      'sound_enabled': soundEnabled ? 1 : 0,
      'rest_sound_path': restSoundPath,
      'pre_alert_enabled': preAlertEnabled ? 1 : 0,
      'pre_alert_seconds': preAlertSeconds,
      'pre_alert_default_action': preAlertDefaultAction.storageValue,
      'pre_alert_title': preAlertTitle,
      'pre_alert_subtitle': preAlertSubtitle,
      'pre_alert_message': preAlertMessage,
      'pre_alert_icon_path': preAlertIconPath,
      'pre_alert_sound_enabled': preAlertSoundEnabled ? 1 : 0,
      'ask_before_break': askBeforeBreak ? 1 : 0,
      'disable_skip': disableSkip ? 1 : 0,
      'timeout_auto_break': timeoutAutoBreak ? 1 : 0,
      'pomodoro_enabled': pomodoroEnabled ? 1 : 0,
      'pomodoro_work_minutes': pomodoroWorkMinutes,
      'pomodoro_short_break_minutes': pomodoroShortBreakMinutes,
      'pomodoro_long_break_minutes': pomodoroLongBreakMinutes,
      'pomodoro_interactive_mode': pomodoroInteractiveMode ? 1 : 0,
      'pomodoro_work_start_sound_enabled': pomodoroWorkStartSoundEnabled
          ? 1
          : 0,
      'pomodoro_work_end_sound_enabled': pomodoroWorkEndSoundEnabled ? 1 : 0,
      'pomodoro_work_start_sound_path': pomodoroWorkStartSoundPath,
      'pomodoro_work_end_sound_path': pomodoroWorkEndSoundPath,
      'active_tip_template_id': activeTipTemplateId,
      'stats_work_image_path': statsWorkImagePath,
      'stats_rest_image_path': statsRestImagePath,
      'stats_skip_image_path': statsSkipImagePath,
      'updated_at': updatedAt.toIso8601String(),
    };
  }

  factory AppSettings.fromMap(Map<String, Object?> map) {
    return AppSettings(
      id: (map['id'] as int?) ?? 1,
      languageCode: (map['language_code'] as String?) ?? 'zh',
      themeMode: appThemeModeFromStorage(map['theme_mode'] as String?),
      useAutoDarkWindow: ((map['use_auto_dark_window'] as int?) ?? 0) == 1,
      autoDarkStartMinute: (map['auto_dark_start_minute'] as int?) ?? 1080,
      autoDarkEndMinute: (map['auto_dark_end_minute'] as int?) ?? 360,
      reminderEnabled: ((map['reminder_enabled'] as int?) ?? 1) == 1,
      warnIntervalMinutes: (map['warn_interval_minutes'] as int?) ?? 20,
      restDurationSeconds: (map['rest_duration_seconds'] as int?) ?? 20,
      statsEnabled: ((map['stats_enabled'] as int?) ?? 1) == 1,
      soundEnabled: ((map['sound_enabled'] as int?) ?? 1) == 1,
      restSoundPath: map['rest_sound_path'] as String?,
      preAlertEnabled: ((map['pre_alert_enabled'] as int?) ?? 1) == 1,
      preAlertSeconds: (map['pre_alert_seconds'] as int?) ?? 15,
      preAlertDefaultAction: reminderActionFromStorage(
        map['pre_alert_default_action'] as String?,
      ),
      preAlertTitle: (map['pre_alert_title'] as String?) ?? '即将进入休息',
      preAlertSubtitle: (map['pre_alert_subtitle'] as String?) ?? '请保存当前进度',
      preAlertMessage:
          (map['pre_alert_message'] as String?) ?? '倒计时结束后建议开始短暂休息',
      preAlertIconPath: map['pre_alert_icon_path'] as String?,
      preAlertSoundEnabled:
          ((map['pre_alert_sound_enabled'] as int?) ?? 0) == 1,
      askBeforeBreak: ((map['ask_before_break'] as int?) ?? 1) == 1,
      disableSkip: ((map['disable_skip'] as int?) ?? 0) == 1,
      timeoutAutoBreak: ((map['timeout_auto_break'] as int?) ?? 1) == 1,
      pomodoroEnabled: ((map['pomodoro_enabled'] as int?) ?? 1) == 1,
      pomodoroWorkMinutes: (map['pomodoro_work_minutes'] as int?) ?? 25,
      pomodoroShortBreakMinutes:
          (map['pomodoro_short_break_minutes'] as int?) ?? 5,
      pomodoroLongBreakMinutes:
          (map['pomodoro_long_break_minutes'] as int?) ?? 15,
      pomodoroInteractiveMode:
          ((map['pomodoro_interactive_mode'] as int?) ?? 1) == 1,
      pomodoroWorkStartSoundEnabled:
          ((map['pomodoro_work_start_sound_enabled'] as int?) ?? 0) == 1,
      pomodoroWorkEndSoundEnabled:
          ((map['pomodoro_work_end_sound_enabled'] as int?) ?? 0) == 1,
      pomodoroWorkStartSoundPath:
          map['pomodoro_work_start_sound_path'] as String?,
      pomodoroWorkEndSoundPath: map['pomodoro_work_end_sound_path'] as String?,
      activeTipTemplateId: map['active_tip_template_id'] as int?,
      statsWorkImagePath: map['stats_work_image_path'] as String?,
      statsRestImagePath: map['stats_rest_image_path'] as String?,
      statsSkipImagePath: map['stats_skip_image_path'] as String?,
      updatedAt:
          DateTime.tryParse((map['updated_at'] as String?) ?? '') ??
          DateTime.now(),
    );
  }
}
