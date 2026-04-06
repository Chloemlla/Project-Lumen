import 'package:sqflite/sqflite.dart';

import 'package:project_lumen/core/storage/db/tables/runtime_state_table.dart';

class RuntimeStateDao {
  RuntimeStateDao(this._database);

  final Database _database;

  Future<Map<String, Object?>?> fetch() async {
    final rows = await _database.query(
      RuntimeStateTable.tableName,
      where: 'id = ?',
      whereArgs: const [1],
      limit: 1,
    );
    return rows.isEmpty ? null : rows.first;
  }

  Future<void> upsert(Map<String, Object?> values) async {
    await _database.insert(
      RuntimeStateTable.tableName,
      values,
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }
}
