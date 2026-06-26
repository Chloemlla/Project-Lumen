import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';

import 'package:project_lumen/core/constants/app_constants.dart';
import 'package:project_lumen/core/logging/app_logger.dart';
import 'package:project_lumen/core/storage/db/database_factory_initializer.dart';
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
    await initializeDatabaseFactoryForPlatform();
    final directory = await getApplicationDocumentsDirectory();
    final path = p.join(directory.path, AppConstants.databaseName);
    appLogger.info('Opening database', {'path': path});
    final db = await openDatabase(
      path,
      version: 2,
      onCreate: (database, version) => _ensureSchema(database),
      onUpgrade: (database, oldVersion, newVersion) => _ensureSchema(database),
      onOpen: _ensureSchema,
    );
    return AppDatabase._(db);
  }

  static Future<void> _ensureSchema(Database database) async {
    appLogger.info('Database schema verification started');
    await database.execute(AppSettingsTable.createSql);
    await database.execute(RuntimeStateTable.createSql);
    await database.execute(DailyEyeStatsTable.createSql);
    await database.execute(DailyPomodoroStatsTable.createSql);
    await database.execute(TipTemplatesTable.createSql);

    await _ensureColumns(database, AppSettingsTable.tableName, const {
      'id': 'INTEGER',
      'language_code': "TEXT NOT NULL DEFAULT 'zh'",
      'theme_mode': "TEXT NOT NULL DEFAULT 'system'",
      'use_auto_dark_window': 'INTEGER NOT NULL DEFAULT 0',
      'auto_dark_start_minute': 'INTEGER NOT NULL DEFAULT 1080',
      'auto_dark_end_minute': 'INTEGER NOT NULL DEFAULT 360',
      'reminder_enabled': 'INTEGER NOT NULL DEFAULT 1',
      'warn_interval_minutes': 'INTEGER NOT NULL DEFAULT 20',
      'rest_duration_seconds': 'INTEGER NOT NULL DEFAULT 20',
      'stats_enabled': 'INTEGER NOT NULL DEFAULT 1',
      'sound_enabled': 'INTEGER NOT NULL DEFAULT 1',
      'rest_sound_path': 'TEXT',
      'pre_alert_enabled': 'INTEGER NOT NULL DEFAULT 1',
      'pre_alert_seconds': 'INTEGER NOT NULL DEFAULT 15',
      'pre_alert_default_action': "TEXT NOT NULL DEFAULT 'start_break'",
      'pre_alert_title': "TEXT NOT NULL DEFAULT ''",
      'pre_alert_subtitle': "TEXT NOT NULL DEFAULT ''",
      'pre_alert_message': "TEXT NOT NULL DEFAULT ''",
      'pre_alert_icon_path': 'TEXT',
      'pre_alert_sound_enabled': 'INTEGER NOT NULL DEFAULT 0',
      'ask_before_break': 'INTEGER NOT NULL DEFAULT 1',
      'disable_skip': 'INTEGER NOT NULL DEFAULT 0',
      'timeout_auto_break': 'INTEGER NOT NULL DEFAULT 1',
      'pomodoro_enabled': 'INTEGER NOT NULL DEFAULT 1',
      'pomodoro_work_minutes': 'INTEGER NOT NULL DEFAULT 25',
      'pomodoro_short_break_minutes': 'INTEGER NOT NULL DEFAULT 5',
      'pomodoro_long_break_minutes': 'INTEGER NOT NULL DEFAULT 15',
      'pomodoro_interactive_mode': 'INTEGER NOT NULL DEFAULT 1',
      'pomodoro_work_start_sound_enabled': 'INTEGER NOT NULL DEFAULT 0',
      'pomodoro_work_end_sound_enabled': 'INTEGER NOT NULL DEFAULT 0',
      'pomodoro_work_start_sound_path': 'TEXT',
      'pomodoro_work_end_sound_path': 'TEXT',
      'active_tip_template_id': 'INTEGER',
      'stats_work_image_path': 'TEXT',
      'stats_rest_image_path': 'TEXT',
      'stats_skip_image_path': 'TEXT',
      'updated_at': "TEXT NOT NULL DEFAULT ''",
    });

    await _ensureColumns(database, RuntimeStateTable.tableName, const {
      'id': 'INTEGER',
      'active_engine': "TEXT NOT NULL DEFAULT 'idle'",
      'reminder_phase': "TEXT NOT NULL DEFAULT 'idle'",
      'reminder_started_at': 'TEXT',
      'next_pre_alert_at': 'TEXT',
      'next_reminder_at': 'TEXT',
      'break_started_at': 'TEXT',
      'break_end_at': 'TEXT',
      'pomodoro_phase': "TEXT NOT NULL DEFAULT 'idle'",
      'pomodoro_phase_started_at': 'TEXT',
      'pomodoro_phase_end_at': 'TEXT',
      'pomodoro_cycle_index': 'INTEGER NOT NULL DEFAULT 1',
      'is_manually_paused': 'INTEGER NOT NULL DEFAULT 0',
      'paused_at': 'TEXT',
      'suspended_until': 'TEXT',
      'last_foreground_at': 'TEXT',
      'last_background_at': 'TEXT',
      'updated_at': "TEXT NOT NULL DEFAULT ''",
    });

    await _ensureColumns(database, DailyEyeStatsTable.tableName, const {
      'stat_date': 'TEXT',
      'working_seconds': 'INTEGER NOT NULL DEFAULT 0',
      'rest_seconds': 'INTEGER NOT NULL DEFAULT 0',
      'skip_count': 'INTEGER NOT NULL DEFAULT 0',
      'completed_break_count': 'INTEGER NOT NULL DEFAULT 0',
      'pre_alert_count': 'INTEGER NOT NULL DEFAULT 0',
      'updated_at': "TEXT NOT NULL DEFAULT ''",
    });

    await _ensureColumns(database, DailyPomodoroStatsTable.tableName, const {
      'stat_date': 'TEXT',
      'completed_tomato_count': 'INTEGER NOT NULL DEFAULT 0',
      'restart_count': 'INTEGER NOT NULL DEFAULT 0',
      'completed_focus_sessions': 'INTEGER NOT NULL DEFAULT 0',
      'total_focus_seconds': 'INTEGER NOT NULL DEFAULT 0',
      'total_break_seconds': 'INTEGER NOT NULL DEFAULT 0',
      'updated_at': "TEXT NOT NULL DEFAULT ''",
    });

    await _ensureColumns(database, TipTemplatesTable.tableName, const {
      'id': 'INTEGER',
      'name': "TEXT NOT NULL DEFAULT ''",
      'is_builtin': 'INTEGER NOT NULL DEFAULT 0',
      'background_type': "TEXT NOT NULL DEFAULT 'solid'",
      'background_value': "TEXT NOT NULL DEFAULT '#14746F'",
      'primary_color': "TEXT NOT NULL DEFAULT '#14746F'",
      'title_text': "TEXT NOT NULL DEFAULT ''",
      'subtitle_text': "TEXT NOT NULL DEFAULT ''",
      'image_path': 'TEXT',
      'show_skip_button': 'INTEGER NOT NULL DEFAULT 1',
      'layout_json': 'TEXT',
      'sort_order': 'INTEGER NOT NULL DEFAULT 0',
      'created_at': "TEXT NOT NULL DEFAULT ''",
      'updated_at': "TEXT NOT NULL DEFAULT ''",
    });
    appLogger.info('Database schema verification completed');
  }

  static Future<void> _ensureColumns(
    Database database,
    String tableName,
    Map<String, String> columns,
  ) async {
    final existingRows = await database.rawQuery(
      'PRAGMA table_info($tableName)',
    );
    final existingNames = existingRows
        .map((row) => row['name'])
        .whereType<String>()
        .toSet();

    for (final entry in columns.entries) {
      if (existingNames.contains(entry.key)) {
        continue;
      }

      appLogger.warning('Adding missing database column', {
        'table': tableName,
        'column': entry.key,
      });
      await database.execute(
        'ALTER TABLE $tableName ADD COLUMN ${entry.key} ${entry.value}',
      );
    }
  }
}
