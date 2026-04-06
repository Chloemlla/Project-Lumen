import 'package:sqflite/sqflite.dart';

import 'package:project_lumen/core/storage/db/tables/app_settings_table.dart';

class AppSettingsDao {
  AppSettingsDao(this._database);

  final Database _database;

  Future<Map<String, Object?>?> fetch() async {
    final rows = await _database.query(
      AppSettingsTable.tableName,
      where: 'id = ?',
      whereArgs: const [1],
      limit: 1,
    );
    return rows.isEmpty ? null : rows.first;
  }

  Future<void> upsert(Map<String, Object?> values) async {
    await _database.insert(
      AppSettingsTable.tableName,
      values,
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }
}
