import 'package:drift/drift.dart';
import 'package:project_eye_mobile/core/storage/db/converters/db_converters.dart';

class DailyPomodoroStatsTable extends Table {
  DailyPomodoroStatsTable();

  static const _dateTime = Iso8601DateTimeConverter();

  TextColumn get statDate => text()();

  IntColumn get completedTomatoCount =>
      integer().withDefault(const Constant(0))();

  IntColumn get restartCount => integer().withDefault(const Constant(0))();

  IntColumn get completedFocusSessions =>
      integer().withDefault(const Constant(0))();

  IntColumn get totalFocusSeconds =>
      integer().withDefault(const Constant(0))();

  IntColumn get totalBreakSeconds =>
      integer().withDefault(const Constant(0))();

  TextColumn get updatedAt =>
      text().map(_dateTime).withDefault(Constant(DateTime(1970)))();

  @override
  Set<Column<Object>> get primaryKey => {statDate};
}

