import 'package:drift/drift.dart';
import 'package:project_eye_mobile/core/enums/active_engine.dart';
import 'package:project_eye_mobile/core/enums/pomodoro_phase.dart';
import 'package:project_eye_mobile/core/enums/reminder_phase.dart';
import 'package:project_eye_mobile/core/storage/db/converters/db_converters.dart';

class RuntimeStateTable extends Table {
  RuntimeStateTable();

  static const _dateTime = Iso8601DateTimeConverter();

  IntColumn get id => integer().withDefault(const Constant(1))();

  TextColumn get activeEngine =>
      textEnum<ActiveEngine>().withDefault(Constant(ActiveEngine.idle))();

  TextColumn get reminderPhase =>
      textEnum<ReminderPhase>().withDefault(Constant(ReminderPhase.idle))();

  TextColumn get reminderStartedAt => text().map(_dateTime).nullable()();

  TextColumn get nextPreAlertAt => text().map(_dateTime).nullable()();

  TextColumn get nextReminderAt => text().map(_dateTime).nullable()();

  TextColumn get breakStartedAt => text().map(_dateTime).nullable()();

  TextColumn get breakEndAt => text().map(_dateTime).nullable()();

  TextColumn get pomodoroPhase =>
      textEnum<PomodoroPhase>().withDefault(Constant(PomodoroPhase.idle))();

  TextColumn get pomodoroPhaseStartedAt => text().map(_dateTime).nullable()();

  TextColumn get pomodoroPhaseEndAt => text().map(_dateTime).nullable()();

  IntColumn get pomodoroCycleIndex =>
      integer().withDefault(const Constant(1))();

  BoolColumn get isManuallyPaused =>
      boolean().withDefault(const Constant(false))();

  TextColumn get pausedAt => text().map(_dateTime).nullable()();

  TextColumn get suspendedUntil => text().map(_dateTime).nullable()();

  TextColumn get lastForegroundAt => text().map(_dateTime).nullable()();

  TextColumn get lastBackgroundAt => text().map(_dateTime).nullable()();

  TextColumn get updatedAt =>
      text().map(_dateTime).withDefault(Constant(DateTime(1970)))();

  @override
  Set<Column<Object>> get primaryKey => {id};
}

