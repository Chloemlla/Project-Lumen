import 'package:flutter/material.dart';

enum AppThemeMode { system, light, dark }

extension AppThemeModeX on AppThemeMode {
  String get storageValue => switch (this) {
    AppThemeMode.system => 'system',
    AppThemeMode.light => 'light',
    AppThemeMode.dark => 'dark',
  };

  ThemeMode get themeMode => switch (this) {
    AppThemeMode.system => ThemeMode.system,
    AppThemeMode.light => ThemeMode.light,
    AppThemeMode.dark => ThemeMode.dark,
  };
}

AppThemeMode appThemeModeFromStorage(String? value) {
  return AppThemeMode.values.firstWhere(
    (mode) => mode.storageValue == value,
    orElse: () => AppThemeMode.system,
  );
}
