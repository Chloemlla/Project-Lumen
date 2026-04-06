import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';

import 'package:project_lumen/core/constants/app_constants.dart';
import 'package:project_lumen/core/storage/db/daos/app_settings_dao.dart';
import 'package:project_lumen/core/storage/db/daos/daily_eye_stats_dao.dart';
import 'package:project_lumen/core/storage/db/daos/daily_pomodoro_stats_dao.dart';
import 'package:project_lumen/core/storage/db/daos/runtime_state_dao.dart';
import 'package:project_lumen/core/storage/db/daos/tip_templates_dao.dart';
import 'package:project_lumen/core/storage/db/tables/app_settings_table.dart';
import 'package:project_lumen/core/storage/db/tables/daily_eye_stats_table.dart';
import 'package:project_lumen/core/storage/db/tables/daily_pomodoro_stats_table.dart';
import 'package:project_lumen/core/storage/db/tables/runtime_state_table.dart';
import 'package:project_lumen/core/storage/db/tables/tip_templates_table.dart';

class AppDatabase {
  AppDatabase._(this.database);

  final Database database;

  late final AppSettingsDao appSettingsDao = AppSettingsDao(database);
  late final RuntimeStateDao runtimeStateDao = RuntimeStateDao(database);
  late final DailyEyeStatsDao dailyEyeStatsDao = DailyEyeStatsDao(database);
  late final DailyPomodoroStatsDao dailyPomodoroStatsDao =
      DailyPomodoroStatsDao(database);
  late final TipTemplatesDao tipTemplatesDao = TipTemplatesDao(database);

  static Future<AppDatabase> open() async {
    final directory = await getApplicationDocumentsDirectory();
    final path = p.join(directory.path, AppConstants.databaseName);
    final db = await openDatabase(
      path,
      version: 1,
      onCreate: (database, version) async {
        await database.execute(AppSettingsTable.createSql);
        await database.execute(RuntimeStateTable.createSql);
        await database.execute(DailyEyeStatsTable.createSql);
        await database.execute(DailyPomodoroStatsTable.createSql);
        await database.execute(TipTemplatesTable.createSql);
      },
    );
    return AppDatabase._(db);
  }
}
