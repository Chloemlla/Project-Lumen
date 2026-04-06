import 'package:drift/drift.dart';
import 'package:project_eye_mobile/core/storage/db/converters/db_converters.dart';

class TipTemplatesTable extends Table {
  TipTemplatesTable();

  static const _dateTime = Iso8601DateTimeConverter();

  IntColumn get id => integer().autoIncrement()();

  TextColumn get name => text()();

  BoolColumn get isBuiltin =>
      boolean().withDefault(const Constant(false))();

  TextColumn get backgroundType =>
      text().withDefault(const Constant('solid'))();

  TextColumn get backgroundValue => text().nullable()();

  TextColumn get primaryColor =>
      text().withDefault(const Constant('#4F6BED'))();

  TextColumn get titleText =>
      text().withDefault(const Constant('休息一下吧'))();

  TextColumn get subtitleText =>
      text().withDefault(const Constant('请将注意力转向远处'))();

  TextColumn get imagePath => text().nullable()();

  BoolColumn get showSkipButton =>
      boolean().withDefault(const Constant(true))();

  TextColumn get layoutJson => text().nullable()();

  IntColumn get sortOrder => integer().withDefault(const Constant(0))();

  TextColumn get createdAt =>
      text().map(_dateTime).withDefault(Constant(DateTime(1970)))();

  TextColumn get updatedAt =>
      text().map(_dateTime).withDefault(Constant(DateTime(1970)))();
}

