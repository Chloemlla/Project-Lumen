import 'package:flutter_riverpod/legacy.dart';
import 'package:project_lumen/app/bootstrap.dart';
import 'package:project_lumen/core/enums/app_theme_mode.dart';
import 'package:project_lumen/features/settings/domain/models/app_settings.dart';
import 'package:project_lumen/features/settings/domain/repositories/settings_repository.dart';

final settingsControllerProvider =
    StateNotifierProvider<SettingsController, AppSettings>((ref) {
      final controller = SettingsController(
        ref.watch(settingsRepositoryProvider),
      );
      controller.hydrate();
      return controller;
    });

class SettingsController extends StateNotifier<AppSettings> {
  SettingsController(this._repository) : super(AppSettings.defaults());

  final SettingsRepository _repository;

  Future<void> hydrate() async {
    state = await _repository.getSettings();
  }

  Future<void> setThemeMode(AppThemeMode mode) async {
    await _save(state.copyWith(themeMode: mode));
  }

  Future<void> setLanguage(String languageCode) async {
    await _save(state.copyWith(languageCode: languageCode));
  }

  Future<void> updateReminder({
    bool? enabled,
    int? warnIntervalMinutes,
    int? restDurationSeconds,
    bool? preAlertEnabled,
    int? preAlertSeconds,
    bool? askBeforeBreak,
    bool? disableSkip,
    bool? timeoutAutoBreak,
  }) async {
    await _save(
      state.copyWith(
        reminderEnabled: enabled,
        warnIntervalMinutes: warnIntervalMinutes,
        restDurationSeconds: restDurationSeconds,
        preAlertEnabled: preAlertEnabled,
        preAlertSeconds: preAlertSeconds,
        askBeforeBreak: askBeforeBreak,
        disableSkip: disableSkip,
        timeoutAutoBreak: timeoutAutoBreak,
      ),
    );
  }

  Future<void> updatePomodoro({
    bool? enabled,
    int? workMinutes,
    int? shortBreakMinutes,
    int? longBreakMinutes,
    bool? interactiveMode,
  }) async {
    await _save(
      state.copyWith(
        pomodoroEnabled: enabled,
        pomodoroWorkMinutes: workMinutes,
        pomodoroShortBreakMinutes: shortBreakMinutes,
        pomodoroLongBreakMinutes: longBreakMinutes,
        pomodoroInteractiveMode: interactiveMode,
      ),
    );
  }

  Future<void> selectTemplate(int templateId) async {
    await _save(state.copyWith(activeTipTemplateId: templateId));
  }

  Future<void> _save(AppSettings next) async {
    state = next;
    await _repository.saveSettings(next);
  }
}
