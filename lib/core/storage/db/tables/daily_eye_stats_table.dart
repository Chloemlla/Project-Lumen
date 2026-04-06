abstract final class DailyEyeStatsTable {
  static const tableName = 'daily_eye_stats';

  static const createSql =
      '''
    CREATE TABLE $tableName (
      stat_date TEXT PRIMARY KEY,
      working_seconds INTEGER NOT NULL DEFAULT 0,
      rest_seconds INTEGER NOT NULL DEFAULT 0,
      skip_count INTEGER NOT NULL DEFAULT 0,
      completed_break_count INTEGER NOT NULL DEFAULT 0,
      pre_alert_count INTEGER NOT NULL DEFAULT 0,
      updated_at TEXT NOT NULL
    )
  ''';
}
