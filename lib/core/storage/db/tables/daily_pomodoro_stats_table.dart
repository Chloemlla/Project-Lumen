abstract final class DailyPomodoroStatsTable {
  static const tableName = 'daily_pomodoro_stats';

  static const createSql =
      '''
    CREATE TABLE $tableName (
      stat_date TEXT PRIMARY KEY,
      completed_tomato_count INTEGER NOT NULL DEFAULT 0,
      restart_count INTEGER NOT NULL DEFAULT 0,
      completed_focus_sessions INTEGER NOT NULL DEFAULT 0,
      total_focus_seconds INTEGER NOT NULL DEFAULT 0,
      total_break_seconds INTEGER NOT NULL DEFAULT 0,
      updated_at TEXT NOT NULL
    )
  ''';
}
