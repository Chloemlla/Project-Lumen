import 'package:drift/drift.dart';
import 'package:project_eye_mobile/core/storage/db/converters/db_converters.dart';

class DailyEyeStatsTable extends Table {
  DailyEyeStatsTable();

  static const _dateTime = Iso8601DateTimeConverter();

  TextColumn get statDate => text()();

  IntColumn get workingSeconds =>
      integer().withDefault(const Constant(0))();

  IntColumn get restSeconds => integer().withDefault(const Constant(0))();

  IntColumn get skipCount => integer().withDefault(const Constant(0))();

  IntColumn get completedBreakCount =>
      integer().withDefault(const Constant(0))();

  IntColumn get preAlertCount => integer().withDefault(const Constant(0))();

  TextColumn get updatedAt =>
      text().map(_dateTime).withDefault(Constant(DateTime(1970)))();

  @override
  Set<Column<Object>> get primaryKey => {statDate};
}

