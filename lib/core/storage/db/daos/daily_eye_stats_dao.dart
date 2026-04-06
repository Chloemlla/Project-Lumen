import 'package:sqflite/sqflite.dart';

import 'package:project_lumen/core/storage/db/tables/daily_eye_stats_table.dart';

class DailyEyeStatsDao {
  DailyEyeStatsDao(this._database);

  final Database _database;

  Future<void> ensureRow(String statDate, String updatedAt) async {
    await _database.insert(DailyEyeStatsTable.tableName, {
      'stat_date': statDate,
      'updated_at': updatedAt,
    }, conflictAlgorithm: ConflictAlgorithm.ignore);
  }

  Future<void> addWorkingSeconds(
    String statDate,
    int seconds,
    String updatedAt,
  ) async {
    await ensureRow(statDate, updatedAt);
    await _database.rawUpdate(
      '''
      UPDATE ${DailyEyeStatsTable.tableName}
      SET working_seconds = working_seconds + ?, updated_at = ?
      WHERE stat_date = ?
      ''',
      [seconds, updatedAt, statDate],
    );
  }

  Future<void> addRestSeconds(
    String statDate,
    int seconds,
    String updatedAt,
  ) async {
    await ensureRow(statDate, updatedAt);
    await _database.rawUpdate(
      '''
      UPDATE ${DailyEyeStatsTable.tableName}
      SET rest_seconds = rest_seconds + ?, updated_at = ?
      WHERE stat_date = ?
      ''',
      [seconds, updatedAt, statDate],
    );
  }

  Future<void> incrementSkip(String statDate, String updatedAt) async {
    await ensureRow(statDate, updatedAt);
    await _database.rawUpdate(
      '''
      UPDATE ${DailyEyeStatsTable.tableName}
      SET skip_count = skip_count + 1, updated_at = ?
      WHERE stat_date = ?
      ''',
      [updatedAt, statDate],
    );
  }

  Future<void> incrementCompletedBreak(
    String statDate,
    String updatedAt,
  ) async {
    await ensureRow(statDate, updatedAt);
    await _database.rawUpdate(
      '''
      UPDATE ${DailyEyeStatsTable.tableName}
      SET completed_break_count = completed_break_count + 1, updated_at = ?
      WHERE stat_date = ?
      ''',
      [updatedAt, statDate],
    );
  }

  Future<void> incrementPreAlert(String statDate, String updatedAt) async {
    await ensureRow(statDate, updatedAt);
    await _database.rawUpdate(
      '''
      UPDATE ${DailyEyeStatsTable.tableName}
      SET pre_alert_count = pre_alert_count + 1, updated_at = ?
      WHERE stat_date = ?
      ''',
      [updatedAt, statDate],
    );
  }

  Future<List<Map<String, Object?>>> fetchSince(String statDate) {
    return _database.query(
      DailyEyeStatsTable.tableName,
      where: 'stat_date >= ?',
      whereArgs: [statDate],
      orderBy: 'stat_date ASC',
    );
  }
}
