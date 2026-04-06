import 'package:drift/drift.dart';

class Iso8601DateTimeConverter extends TypeConverter<DateTime, String> {
  const Iso8601DateTimeConverter();

  @override
  DateTime fromSql(String fromDb) => DateTime.parse(fromDb);

  @override
  String toSql(DateTime value) => value.toUtc().toIso8601String();
}

