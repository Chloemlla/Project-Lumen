import 'package:drift/drift.dart';
import 'package:project_eye_mobile/core/enums/reminder_action.dart';
import 'package:project_eye_mobile/core/storage/db/converters/db_converters.dart';

class AppSettingsTable extends Table {
  AppSettingsTable();

  static const _dateTime = Iso8601DateTimeConverter();

  IntColumn get id => integer().withDefault(const Constant(1))();

  TextColumn get languageCode => text().withDefault(const Constant('zh'))();

  TextColumn get themeMode => text().withDefault(const Constant('system'))();

  BoolColumn get useAutoDarkWindow =>
      boolean().withDefault(const Constant(false))();

  IntColumn get autoDarkStartMinute =>
      integer().withDefault(const Constant(1080))();

  IntColumn get autoDarkEndMinute =>
      integer().withDefault(const Constant(360))();

  BoolColumn get reminderEnabled =>
      boolean().withDefault(const Constant(true))();

  IntColumn get warnIntervalMinutes =>
      integer().withDefault(const Constant(20))();

  IntColumn get restDurationSeconds =>
      integer().withDefault(const Constant(20))();

  BoolColumn get statsEnabled =>
      boolean().withDefault(const Constant(true))();

  BoolColumn get soundEnabled =>
      boolean().withDefault(const Constant(true))();

  TextColumn get restSoundPath => text().nullable()();

  BoolColumn get preAlertEnabled =>
      boolean().withDefault(const Constant(false))();

  IntColumn get preAlertSeconds =>
      integer().withDefault(const Constant(20))();

  TextColumn get preAlertDefaultAction =>
      textEnum<ReminderAction>().withDefault(
        Constant(ReminderAction.startBreak),
      )();

  TextColumn get preAlertTitle =>
      text().withDefault(const Constant('Project Eye'))();

  TextColumn get preAlertSubtitle =>
      text().withDefault(const Constant('提醒剩余 {t} 秒'))();

  TextColumn get preAlertMessage =>
      text().withDefault(const Constant('您已持续用眼一段时间，休息一会吧！'))();

  TextColumn get preAlertIconPath => text().nullable()();

  BoolColumn get preAlertSoundEnabled =>
      boolean().withDefault(const Constant(true))();

  BoolColumn get askBeforeBreak =>
      boolean().withDefault(const Constant(true))();

  BoolColumn get disableSkip =>
      boolean().withDefault(const Constant(false))();

  BoolColumn get timeoutAutoBreak =>
      boolean().withDefault(const Constant(true))();

  BoolColumn get pomodoroEnabled =>
      boolean().withDefault(const Constant(false))();

  IntColumn get pomodoroWorkMinutes =>
      integer().withDefault(const Constant(25))();

  IntColumn get pomodoroShortBreakMinutes =>
      integer().withDefault(const Constant(5))();

  IntColumn get pomodoroLongBreakMinutes =>
      integer().withDefault(const Constant(30))();

  BoolColumn get pomodoroInteractiveMode =>
      boolean().withDefault(const Constant(false))();

  BoolColumn get pomodoroWorkStartSoundEnabled =>
      boolean().withDefault(const Constant(true))();

  BoolColumn get pomodoroWorkEndSoundEnabled =>
      boolean().withDefault(const Constant(true))();

  TextColumn get pomodoroWorkStartSoundPath => text().nullable()();

  TextColumn get pomodoroWorkEndSoundPath => text().nullable()();

  IntColumn get activeTipTemplateId =>
      integer().withDefault(const Constant(1))();

  TextColumn get statsWorkImagePath => text().nullable()();

  TextColumn get statsRestImagePath => text().nullable()();

  TextColumn get statsSkipImagePath => text().nullable()();

  TextColumn get updatedAt =>
      text().map(_dateTime).withDefault(Constant(DateTime(1970)))();

  @override
  Set<Column<Object>> get primaryKey => {id};
}

