import 'package:intl/intl.dart';

extension DateTimeX on DateTime {
  String get isoDate => DateFormat('yyyy-MM-dd').format(this);
  String get hourMinuteLabel => DateFormat('HH:mm').format(this);
  String get monthDayLabel => DateFormat('MM/dd').format(this);
}

DateTime? parseDateTime(Object? value) {
  if (value == null) {
    return null;
  }

  return DateTime.tryParse(value.toString());
}

String? toIsoOrNull(DateTime? value) => value?.toIso8601String();
