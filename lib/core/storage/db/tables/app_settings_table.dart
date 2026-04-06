abstract final class AppSettingsTable {
  static const tableName = 'app_settings';

  static const createSql =
      '''
    CREATE TABLE $tableName (
      id INTEGER PRIMARY KEY,
      language_code TEXT NOT NULL,
      theme_mode TEXT NOT NULL,
      use_auto_dark_window INTEGER NOT NULL DEFAULT 0,
      auto_dark_start_minute INTEGER NOT NULL DEFAULT 1080,
      auto_dark_end_minute INTEGER NOT NULL DEFAULT 360,
      reminder_enabled INTEGER NOT NULL DEFAULT 1,
      warn_interval_minutes INTEGER NOT NULL DEFAULT 20,
      rest_duration_seconds INTEGER NOT NULL DEFAULT 20,
      stats_enabled INTEGER NOT NULL DEFAULT 1,
      sound_enabled INTEGER NOT NULL DEFAULT 1,
      rest_sound_path TEXT,
      pre_alert_enabled INTEGER NOT NULL DEFAULT 1,
      pre_alert_seconds INTEGER NOT NULL DEFAULT 15,
      pre_alert_default_action TEXT NOT NULL DEFAULT 'start_break',
      pre_alert_title TEXT NOT NULL DEFAULT '即将进入休息',
      pre_alert_subtitle TEXT NOT NULL DEFAULT '请保存当前进度',
      pre_alert_message TEXT NOT NULL DEFAULT '倒计时结束后建议开始短暂休息',
      pre_alert_icon_path TEXT,
      pre_alert_sound_enabled INTEGER NOT NULL DEFAULT 0,
      ask_before_break INTEGER NOT NULL DEFAULT 1,
      disable_skip INTEGER NOT NULL DEFAULT 0,
      timeout_auto_break INTEGER NOT NULL DEFAULT 1,
      pomodoro_enabled INTEGER NOT NULL DEFAULT 1,
      pomodoro_work_minutes INTEGER NOT NULL DEFAULT 25,
      pomodoro_short_break_minutes INTEGER NOT NULL DEFAULT 5,
      pomodoro_long_break_minutes INTEGER NOT NULL DEFAULT 15,
      pomodoro_interactive_mode INTEGER NOT NULL DEFAULT 1,
      pomodoro_work_start_sound_enabled INTEGER NOT NULL DEFAULT 0,
      pomodoro_work_end_sound_enabled INTEGER NOT NULL DEFAULT 0,
      pomodoro_work_start_sound_path TEXT,
      pomodoro_work_end_sound_path TEXT,
      active_tip_template_id INTEGER,
      stats_work_image_path TEXT,
      stats_rest_image_path TEXT,
      stats_skip_image_path TEXT,
      updated_at TEXT NOT NULL
    )
  ''';
}
