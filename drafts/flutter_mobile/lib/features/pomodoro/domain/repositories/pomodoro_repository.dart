import 'package:project_eye_mobile/features/pomodoro/domain/models/pomodoro_runtime_state.dart';
import 'package:project_eye_mobile/features/settings/domain/models/app_settings.dart';

abstract class PomodoroRepository {
  Future<AppSettings> getSettings();

  Future<PomodoroRuntimeState> getRuntimeState();

  Future<void> saveRuntimeState(PomodoroRuntimeState state);

  Future<void> addFocusDuration(Duration duration, {DateTime? at});

  Future<void> addBreakDuration(Duration duration, {DateTime? at});

  Future<void> incrementCompletedFocusSessions({DateTime? at});

  Future<void> incrementCompletedTomatoCount({DateTime? at});

  Future<void> incrementRestartCount({DateTime? at});
}
