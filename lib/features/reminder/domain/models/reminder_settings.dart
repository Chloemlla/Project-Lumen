import 'package:project_lumen/features/settings/domain/models/app_settings.dart';

class ReminderSettings {
  const ReminderSettings({
    required this.warnInterval,
    required this.restDuration,
    required this.preAlertEnabled,
    required this.preAlertDuration,
    required this.askBeforeBreak,
    required this.disableSkip,
    required this.timeoutAutoBreak,
  });

  final Duration warnInterval;
  final Duration restDuration;
  final bool preAlertEnabled;
  final Duration preAlertDuration;
  final bool askBeforeBreak;
  final bool disableSkip;
  final bool timeoutAutoBreak;

  factory ReminderSettings.fromAppSettings(AppSettings settings) {
    return ReminderSettings(
      warnInterval: settings.warnInterval,
      restDuration: settings.restDuration,
      preAlertEnabled: settings.preAlertEnabled,
      preAlertDuration: settings.preAlertDuration,
      askBeforeBreak: settings.askBeforeBreak,
      disableSkip: settings.disableSkip,
      timeoutAutoBreak: settings.timeoutAutoBreak,
    );
  }
}
