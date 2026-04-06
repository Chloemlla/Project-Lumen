import 'package:project_lumen/core/enums/active_engine.dart';
import 'package:project_lumen/core/enums/pomodoro_phase.dart';
import 'package:project_lumen/core/enums/reminder_phase.dart';
import 'package:project_lumen/core/services/clock_service.dart';
import 'package:project_lumen/core/storage/db/daos/app_settings_dao.dart';
import 'package:project_lumen/core/storage/db/daos/daily_pomodoro_stats_dao.dart';
import 'package:project_lumen/core/storage/db/daos/runtime_state_dao.dart';
import 'package:project_lumen/core/utils/date_time_x.dart';
import 'package:project_lumen/features/pomodoro/domain/models/pomodoro_runtime_state.dart';
import 'package:project_lumen/features/settings/domain/models/app_settings.dart';

abstract class PomodoroRepository {
  Future<void> ensureInitialized();

  Future<AppSettings> getSettings();

  Future<PomodoroRuntimeState> getRuntimeState();

  Future<void> saveRuntimeState(PomodoroRuntimeState state);

  Future<void> addFocusDuration(Duration duration, {DateTime? at});

  Future<void> addBreakDuration(Duration duration, {DateTime? at});

  Future<void> incrementCompletedFocusSessions({DateTime? at});

  Future<void> incrementCompletedTomatoCount({DateTime? at});

  Future<void> incrementRestartCount({DateTime? at});
}

class LocalPomodoroRepository implements PomodoroRepository {
  LocalPomodoroRepository({
    required RuntimeStateDao runtimeStateDao,
    required DailyPomodoroStatsDao dailyPomodoroStatsDao,
    required AppSettingsDao appSettingsDao,
    required ClockService clockService,
  }) : _runtimeStateDao = runtimeStateDao,
       _dailyPomodoroStatsDao = dailyPomodoroStatsDao,
       _appSettingsDao = appSettingsDao,
       _clockService = clockService;

  final RuntimeStateDao _runtimeStateDao;
  final DailyPomodoroStatsDao _dailyPomodoroStatsDao;
  final AppSettingsDao _appSettingsDao;
  final ClockService _clockService;

  @override
  Future<void> ensureInitialized() async {
    final row = await _runtimeStateDao.fetch();
    if (row != null) {
      return;
    }

    await _runtimeStateDao.upsert(_defaultRuntimeStateRow());
  }

  @override
  Future<AppSettings> getSettings() async {
    final row = await _appSettingsDao.fetch();
    return row == null ? AppSettings.defaults() : AppSettings.fromMap(row);
  }

  @override
  Future<PomodoroRuntimeState> getRuntimeState() async {
    final row = await _runtimeStateDao.fetch();
    if (row == null) {
      return const PomodoroRuntimeState.idle();
    }

    return PomodoroRuntimeState(
      activeEngine: activeEngineFromStorage(row['active_engine'] as String?),
      phase: pomodoroPhaseFromStorage(row['pomodoro_phase'] as String?),
      cycleIndex: (row['pomodoro_cycle_index'] as int?) ?? 1,
      phaseStartedAt: parseDateTime(row['pomodoro_phase_started_at']),
      phaseEndAt: parseDateTime(row['pomodoro_phase_end_at']),
      isManuallyPaused: ((row['is_manually_paused'] as int?) ?? 0) == 1,
      pausedAt: parseDateTime(row['paused_at']),
      suspendedUntil: parseDateTime(row['suspended_until']),
      updatedAt: parseDateTime(row['updated_at']),
    );
  }

  @override
  Future<void> saveRuntimeState(PomodoroRuntimeState state) async {
    final current = await _runtimeStateDao.fetch() ?? _defaultRuntimeStateRow();
    await _runtimeStateDao.upsert({
      ...current,
      'id': 1,
      'active_engine': state.activeEngine.storageValue,
      'pomodoro_phase': state.phase.storageValue,
      'pomodoro_phase_started_at': toIsoOrNull(state.phaseStartedAt),
      'pomodoro_phase_end_at': toIsoOrNull(state.phaseEndAt),
      'pomodoro_cycle_index': state.cycleIndex,
      'is_manually_paused': state.isManuallyPaused ? 1 : 0,
      'paused_at': toIsoOrNull(state.pausedAt),
      'suspended_until': toIsoOrNull(state.suspendedUntil),
      'updated_at': toIsoOrNull(state.updatedAt ?? _clockService.now()),
      'reminder_phase':
          current['reminder_phase'] ?? ReminderPhase.idle.storageValue,
    });
  }

  @override
  Future<void> addBreakDuration(Duration duration, {DateTime? at}) async {
    if (duration <= Duration.zero) {
      return;
    }

    final effectiveAt = at ?? _clockService.now();
    await _dailyPomodoroStatsDao.addBreakSeconds(
      effectiveAt.isoDate,
      duration.inSeconds,
      effectiveAt.toIso8601String(),
    );
  }

  @override
  Future<void> addFocusDuration(Duration duration, {DateTime? at}) async {
    if (duration <= Duration.zero) {
      return;
    }

    final effectiveAt = at ?? _clockService.now();
    await _dailyPomodoroStatsDao.addFocusProgress(
      effectiveAt.isoDate,
      focusSeconds: duration.inSeconds,
      completedTomato: false,
      incrementFocusSessions: false,
      updatedAt: effectiveAt.toIso8601String(),
    );
  }

  @override
  Future<void> incrementCompletedFocusSessions({DateTime? at}) async {
    final effectiveAt = at ?? _clockService.now();
    await _dailyPomodoroStatsDao.addFocusProgress(
      effectiveAt.isoDate,
      focusSeconds: 0,
      completedTomato: false,
      incrementFocusSessions: true,
      updatedAt: effectiveAt.toIso8601String(),
    );
  }

  @override
  Future<void> incrementCompletedTomatoCount({DateTime? at}) async {
    final effectiveAt = at ?? _clockService.now();
    await _dailyPomodoroStatsDao.addFocusProgress(
      effectiveAt.isoDate,
      focusSeconds: 0,
      completedTomato: true,
      incrementFocusSessions: false,
      updatedAt: effectiveAt.toIso8601String(),
    );
  }

  @override
  Future<void> incrementRestartCount({DateTime? at}) async {
    final effectiveAt = at ?? _clockService.now();
    await _dailyPomodoroStatsDao.incrementRestart(
      effectiveAt.isoDate,
      effectiveAt.toIso8601String(),
    );
  }

  Map<String, Object?> _defaultRuntimeStateRow() {
    final now = _clockService.now().toIso8601String();
    return {
      'id': 1,
      'active_engine': ActiveEngine.idle.storageValue,
      'reminder_phase': ReminderPhase.idle.storageValue,
      'pomodoro_phase': PomodoroPhase.idle.storageValue,
      'pomodoro_cycle_index': 1,
      'is_manually_paused': 0,
      'updated_at': now,
    };
  }
}
