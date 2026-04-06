import 'package:project_lumen/core/enums/active_engine.dart';
import 'package:project_lumen/core/enums/pomodoro_phase.dart';
import 'package:project_lumen/core/enums/reminder_phase.dart';
import 'package:project_lumen/core/services/clock_service.dart';
import 'package:project_lumen/core/storage/db/daos/app_settings_dao.dart';
import 'package:project_lumen/core/storage/db/daos/daily_eye_stats_dao.dart';
import 'package:project_lumen/core/storage/db/daos/runtime_state_dao.dart';
import 'package:project_lumen/core/utils/date_time_x.dart';
import 'package:project_lumen/features/reminder/domain/models/reminder_runtime_state.dart';
import 'package:project_lumen/features/settings/domain/models/app_settings.dart';

abstract class ReminderRepository {
  Future<void> ensureInitialized();

  Future<AppSettings> getSettings();

  Future<ReminderRuntimeState> getRuntimeState();

  Future<void> saveRuntimeState(ReminderRuntimeState state);

  Future<void> addWorkingDuration(Duration duration, {DateTime? at});

  Future<void> addRestDuration(Duration duration, {DateTime? at});

  Future<void> incrementSkipCount({DateTime? at});

  Future<void> incrementCompletedBreakCount({DateTime? at});

  Future<void> incrementPreAlertCount({DateTime? at});
}

class LocalReminderRepository implements ReminderRepository {
  LocalReminderRepository({
    required RuntimeStateDao runtimeStateDao,
    required DailyEyeStatsDao dailyEyeStatsDao,
    required AppSettingsDao appSettingsDao,
    required ClockService clockService,
  }) : _runtimeStateDao = runtimeStateDao,
       _dailyEyeStatsDao = dailyEyeStatsDao,
       _appSettingsDao = appSettingsDao,
       _clockService = clockService;

  final RuntimeStateDao _runtimeStateDao;
  final DailyEyeStatsDao _dailyEyeStatsDao;
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
  Future<ReminderRuntimeState> getRuntimeState() async {
    final row = await _runtimeStateDao.fetch();
    if (row == null) {
      return const ReminderRuntimeState.idle();
    }

    return ReminderRuntimeState(
      activeEngine: activeEngineFromStorage(row['active_engine'] as String?),
      phase: reminderPhaseFromStorage(row['reminder_phase'] as String?),
      reminderStartedAt: parseDateTime(row['reminder_started_at']),
      nextPreAlertAt: parseDateTime(row['next_pre_alert_at']),
      nextReminderAt: parseDateTime(row['next_reminder_at']),
      breakStartedAt: parseDateTime(row['break_started_at']),
      breakEndAt: parseDateTime(row['break_end_at']),
      isManuallyPaused: ((row['is_manually_paused'] as int?) ?? 0) == 1,
      pausedAt: parseDateTime(row['paused_at']),
      suspendedUntil: parseDateTime(row['suspended_until']),
      updatedAt: parseDateTime(row['updated_at']),
    );
  }

  @override
  Future<void> saveRuntimeState(ReminderRuntimeState state) async {
    final current = await _runtimeStateDao.fetch() ?? _defaultRuntimeStateRow();
    await _runtimeStateDao.upsert({
      ...current,
      'id': 1,
      'active_engine': state.activeEngine.storageValue,
      'reminder_phase': state.phase.storageValue,
      'reminder_started_at': toIsoOrNull(state.reminderStartedAt),
      'next_pre_alert_at': toIsoOrNull(state.nextPreAlertAt),
      'next_reminder_at': toIsoOrNull(state.nextReminderAt),
      'break_started_at': toIsoOrNull(state.breakStartedAt),
      'break_end_at': toIsoOrNull(state.breakEndAt),
      'is_manually_paused': state.isManuallyPaused ? 1 : 0,
      'paused_at': toIsoOrNull(state.pausedAt),
      'suspended_until': toIsoOrNull(state.suspendedUntil),
      'updated_at': toIsoOrNull(state.updatedAt ?? _clockService.now()),
      'pomodoro_phase':
          current['pomodoro_phase'] ?? PomodoroPhase.idle.storageValue,
      'pomodoro_cycle_index': current['pomodoro_cycle_index'] ?? 1,
    });
  }

  @override
  Future<void> addWorkingDuration(Duration duration, {DateTime? at}) async {
    if (duration <= Duration.zero) {
      return;
    }

    final effectiveAt = at ?? _clockService.now();
    await _dailyEyeStatsDao.addWorkingSeconds(
      effectiveAt.isoDate,
      duration.inSeconds,
      effectiveAt.toIso8601String(),
    );
  }

  @override
  Future<void> addRestDuration(Duration duration, {DateTime? at}) async {
    if (duration <= Duration.zero) {
      return;
    }

    final effectiveAt = at ?? _clockService.now();
    await _dailyEyeStatsDao.addRestSeconds(
      effectiveAt.isoDate,
      duration.inSeconds,
      effectiveAt.toIso8601String(),
    );
  }

  @override
  Future<void> incrementCompletedBreakCount({DateTime? at}) async {
    final effectiveAt = at ?? _clockService.now();
    await _dailyEyeStatsDao.incrementCompletedBreak(
      effectiveAt.isoDate,
      effectiveAt.toIso8601String(),
    );
  }

  @override
  Future<void> incrementPreAlertCount({DateTime? at}) async {
    final effectiveAt = at ?? _clockService.now();
    await _dailyEyeStatsDao.incrementPreAlert(
      effectiveAt.isoDate,
      effectiveAt.toIso8601String(),
    );
  }

  @override
  Future<void> incrementSkipCount({DateTime? at}) async {
    final effectiveAt = at ?? _clockService.now();
    await _dailyEyeStatsDao.incrementSkip(
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
