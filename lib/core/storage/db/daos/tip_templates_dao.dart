import 'package:sqflite/sqflite.dart';

import 'package:project_lumen/core/storage/db/tables/tip_templates_table.dart';

class TipTemplatesDao {
  TipTemplatesDao(this._database);

  final Database _database;

  Future<int> count() async {
    final result = await _database.rawQuery(
      'SELECT COUNT(*) AS total FROM ${TipTemplatesTable.tableName}',
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Future<List<Map<String, Object?>>> fetchAll() {
    return _database.query(
      TipTemplatesTable.tableName,
      orderBy: 'sort_order ASC, id ASC',
    );
  }

  Future<void> insert(Map<String, Object?> values) async {
    await _database.insert(
      TipTemplatesTable.tableName,
      values,
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> updateById(int id, Map<String, Object?> values) async {
    await _database.update(
      TipTemplatesTable.tableName,
      values,
      where: 'id = ?',
      whereArgs: [id],
    );
  }
}
