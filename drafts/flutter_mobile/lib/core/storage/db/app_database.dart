import 'package:drift/drift.dart';
import 'package:project_eye_mobile/core/storage/db/tables/app_settings_table.dart';
import 'package:project_eye_mobile/core/storage/db/tables/daily_eye_stats_table.dart';
import 'package:project_eye_mobile/core/storage/db/tables/daily_pomodoro_stats_table.dart';
import 'package:project_eye_mobile/core/storage/db/tables/runtime_state_table.dart';
import 'package:project_eye_mobile/core/storage/db/tables/tip_templates_table.dart';

part 'app_database.g.dart';

@DriftDatabase(
  tables: [
    AppSettingsTable,
    RuntimeStateTable,
    DailyEyeStatsTable,
    DailyPomodoroStatsTable,
    TipTemplatesTable,
  ],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase(QueryExecutor executor) : super(executor);

  @override
  int get schemaVersion => 1;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (m) async {
          await m.createAll();
        },
        onUpgrade: (m, from, to) async {},
        beforeOpen: (details) async {
          await customStatement('PRAGMA foreign_keys = ON');
        },
      );
}
