import 'package:flutter/widgets.dart';

abstract final class AppConstants {
  static const appName = 'Project-Lumen';
  static const packageName = 'project_lumen';
  static const databaseName = 'project_lumen_mobile.db';
  static const defaultLocale = Locale('zh');
  static const reminderIntervalMinutes = 20;
  static const restDurationSeconds = 20;
  static const preAlertSeconds = 15;
  static const pomodoroWorkMinutes = 25;
  static const pomodoroShortBreakMinutes = 5;
  static const pomodoroLongBreakMinutes = 15;
}
