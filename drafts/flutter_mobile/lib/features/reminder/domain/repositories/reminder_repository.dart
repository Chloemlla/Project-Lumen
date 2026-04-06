import 'package:project_eye_mobile/features/reminder/domain/models/reminder_runtime_state.dart';
import 'package:project_eye_mobile/features/settings/domain/models/app_settings.dart';

abstract class ReminderRepository {
  Future<AppSettings> getSettings();

  Future<ReminderRuntimeState> getRuntimeState();

  Future<void> saveRuntimeState(ReminderRuntimeState state);

  Future<void> addWorkingDuration(Duration duration, {DateTime? at});

  Future<void> addRestDuration(Duration duration, {DateTime? at});

  Future<void> incrementSkipCount({DateTime? at});

  Future<void> incrementCompletedBreakCount({DateTime? at});

  Future<void> incrementPreAlertCount({DateTime? at});
}

