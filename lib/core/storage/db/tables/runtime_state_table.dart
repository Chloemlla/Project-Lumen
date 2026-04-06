abstract final class RuntimeStateTable {
  static const tableName = 'runtime_state';

  static const createSql =
      '''
    CREATE TABLE $tableName (
      id INTEGER PRIMARY KEY,
      active_engine TEXT NOT NULL DEFAULT 'idle',
      reminder_phase TEXT NOT NULL DEFAULT 'idle',
      reminder_started_at TEXT,
      next_pre_alert_at TEXT,
      next_reminder_at TEXT,
      break_started_at TEXT,
      break_end_at TEXT,
      pomodoro_phase TEXT NOT NULL DEFAULT 'idle',
      pomodoro_phase_started_at TEXT,
      pomodoro_phase_end_at TEXT,
      pomodoro_cycle_index INTEGER NOT NULL DEFAULT 1,
      is_manually_paused INTEGER NOT NULL DEFAULT 0,
      paused_at TEXT,
      suspended_until TEXT,
      last_foreground_at TEXT,
      last_background_at TEXT,
      updated_at TEXT NOT NULL
    )
  ''';
}
