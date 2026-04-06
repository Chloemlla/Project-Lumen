import 'package:sqflite/sqflite.dart';

import 'package:project_lumen/core/storage/db/tables/daily_pomodoro_stats_table.dart';

class DailyPomodoroStatsDao {
  DailyPomodoroStatsDao(this._database);

  final Database _database;

  Future<void> ensureRow(String statDate, String updatedAt) async {
    await _database.insert(
      DailyPomodoroStatsTable.tableName,
      {'stat_date': statDate, 'updated_at': updatedAt},
      conflictAlgorithm: ConflictAlgorithm.ignore,
    );
  }

  Future<void> addFocusProgress(
    String statDate, {
    required int focusSeconds,
    required bool completedTomato,
    required bool incrementFocusSessions,
    required String updatedAt,
  }) async {
    await ensureRow(statDate, updatedAt);
    await _database.rawUpdate(
      '''
      UPDATE ${DailyPomodoroStatsTable.tableName}
      SET completed_focus_sessions = completed_focus_sessions + ?,
          total_focus_seconds = total_focus_seconds + ?,
          completed_tomato_count = completed_tomato_count + ?,
          updated_at = ?
      WHERE stat_date = ?
      ''',
      [
        incrementFocusSessions ? 1 : 0,
        focusSeconds,
        completedTomato ? 1 : 0,
        updatedAt,
        statDate,
      ],
    );
  }

  Future<void> addBreakSeconds(
    String statDate,
    int seconds,
    String updatedAt,
  ) async {
    await ensureRow(statDate, updatedAt);
    await _database.rawUpdate(
      '''
      UPDATE ${DailyPomodoroStatsTable.tableName}
      SET total_break_seconds = total_break_seconds + ?, updated_at = ?
      WHERE stat_date = ?
      ''',
      [seconds, updatedAt, statDate],
    );
  }

  Future<void> incrementRestart(String statDate, String updatedAt) async {
    await ensureRow(statDate, updatedAt);
    await _database.rawUpdate(
      '''
      UPDATE ${DailyPomodoroStatsTable.tableName}
      SET restart_count = restart_count + 1, updated_at = ?
      WHERE stat_date = ?
      ''',
      [updatedAt, statDate],
    );
  }

  Future<List<Map<String, Object?>>> fetchSince(String statDate) {
    return _database.query(
      DailyPomodoroStatsTable.tableName,
      where: 'stat_date >= ?',
      whereArgs: [statDate],
      orderBy: 'stat_date ASC',
    );
  }
}
