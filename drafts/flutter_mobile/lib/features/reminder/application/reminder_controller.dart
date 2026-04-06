import 'package:project_eye_mobile/core/enums/active_engine.dart';
import 'package:project_eye_mobile/core/enums/reminder_phase.dart';
import 'package:project_eye_mobile/core/services/audio_service.dart';
import 'package:project_eye_mobile/core/services/clock_service.dart';
import 'package:project_eye_mobile/core/services/notification_service.dart';
import 'package:project_eye_mobile/features/reminder/domain/models/reminder_runtime_state.dart';
import 'package:project_eye_mobile/features/reminder/domain/repositories/reminder_repository.dart';
import 'package:project_eye_mobile/features/settings/domain/models/app_settings.dart';
import 'package:riverpod/riverpod.dart';

class ReminderController extends StateNotifier<ReminderRuntimeState> {
  ReminderController({
    required ReminderRepository repository,
    required AppNotificationService notificationService,
    required AppAudioService audioService,
    required ClockService clockService,
  })  : _repository = repository,
        _notificationService = notificationService,
        _audioService = audioService,
        _clockService = clockService,
        super(const ReminderRuntimeState.idle());

  final ReminderRepository _repository;
  final AppNotificationService _notificationService;
  final AppAudioService _audioService;
  final ClockService _clockService;

  Future<void> hydrate() async {
    state = await _repository.getRuntimeState();
    await recover();
  }

  Future<void> start() async {
    final settings = await _repository.getSettings();
    final now = _clockService.now();
    await _beginWorkingCycle(settings: settings, startedAt: now);
  }

  Future<void> stop() async {
    await _notificationService.cancelReminderNotifications();
    await _saveState(const ReminderRuntimeState.idle());
  }

  Future<void> recover() async {
    final settings = await _repository.getSettings();
    final saved = await _repository.getRuntimeState();
    final now = _clockService.now();
    state = saved;

    if (saved.activeEngine != ActiveEngine.reminder ||
        saved.phase == ReminderPhase.idle) {
      return;
    }

    if (saved.phase == ReminderPhase.paused) {
      final suspendedUntil = saved.suspendedUntil;
      if (suspendedUntil != null && !now.isBefore(suspendedUntil)) {
        await resume();
      }
      return;
    }

    if (saved.phase == ReminderPhase.resting) {
      final breakEndAt = saved.breakEndAt;
      if (breakEndAt == null) {
        await start();
        return;
      }

      if (!now.isBefore(breakEndAt)) {
        await _finishBreak(
          settings: settings,
          source: saved,
          effectiveAt: breakEndAt,
          resumeAt: now,
        );
      } else {
        await _notificationService.cancelReminderNotifications();
        await _notificationService.scheduleBreakFinished(
          scheduledAt: breakEndAt,
        );
      }
      return;
    }

    final nextReminderAt = saved.nextReminderAt;
    final nextPreAlertAt = saved.nextPreAlertAt;

    if (nextReminderAt == null) {
      await start();
      return;
    }

    if (!now.isBefore(nextReminderAt)) {
      await _handleReminderDueInternal(
        settings: settings,
        source: saved,
        effectiveAt: nextReminderAt,
        resumeAt: now,
      );
      return;
    }

    if (nextPreAlertAt != null && !now.isBefore(nextPreAlertAt)) {
      await _enterPreAlert(
        source: saved,
        effectiveAt: nextPreAlertAt,
        incrementCounter: false,
      );
      return;
    }

    await _rescheduleWorkingNotifications(settings: settings, source: saved);
  }

  Future<void> handlePreAlertDue() async {
    if (state.phase != ReminderPhase.working &&
        state.phase != ReminderPhase.preAlert) {
      return;
    }

    await _enterPreAlert(
      source: state,
      effectiveAt: _clockService.now(),
      incrementCounter: true,
    );
  }

  Future<void> handleReminderDue() async {
    final settings = await _repository.getSettings();
    final now = _clockService.now();
    await _handleReminderDueInternal(
      settings: settings,
      source: state,
      effectiveAt: now,
      resumeAt: now,
    );
  }

  Future<void> startBreak({bool isAuto = false}) async {
    final settings = await _repository.getSettings();
    final now = _clockService.now();
    await _enterResting(
      settings: settings,
      source: state,
      breakStartedAt: now,
      breakEndAt: now.add(settings.restDuration),
    );
  }

  Future<void> skipCurrentBreak() async {
    final settings = await _repository.getSettings();
    final now = _clockService.now();
    await _repository.incrementSkipCount(at: now);
    await _beginWorkingCycle(settings: settings, startedAt: now);
  }

  Future<void> handleActionTimeout() async {
    final settings = await _repository.getSettings();

    if (settings.disableSkip || settings.timeoutAutoBreak) {
      await startBreak(isAuto: true);
      return;
    }

    await skipCurrentBreak();
  }

  Future<void> completeBreak({bool finishedNaturally = true}) async {
    final settings = await _repository.getSettings();
    final now = _clockService.now();
    await _finishBreak(
      settings: settings,
      source: state,
      effectiveAt: now,
      resumeAt: now,
      finishedNaturally: finishedNaturally,
    );
  }

  Future<void> pauseUntil(DateTime? until) async {
    final now = _clockService.now();
    final settled = await _settleCurrentPhaseForPause(now);

    await _notificationService.cancelReminderNotifications();
    await _saveState(
      settled.copyWith(
        activeEngine: ActiveEngine.reminder,
        phase: ReminderPhase.paused,
        isManuallyPaused: true,
        pausedAt: now,
        suspendedUntil: until,
        clearNextPreAlertAt: true,
        clearNextReminderAt: true,
        clearBreakStartedAt: true,
        clearBreakEndAt: true,
      ),
    );
  }

  Future<void> resume() async {
    final settings = await _repository.getSettings();
    final now = _clockService.now();
    await _beginWorkingCycle(settings: settings, startedAt: now);
  }

  Future<void> _handleReminderDueInternal({
    required AppSettings settings,
    required ReminderRuntimeState source,
    required DateTime effectiveAt,
    required DateTime resumeAt,
  }) async {
    final settled = await _settleWorkingCycle(
      source: source,
      until: effectiveAt,
    );

    if (!settings.askBeforeBreak || settings.disableSkip) {
      await _recoverOrEnterBreak(
        settings: settings,
        source: settled,
        breakStartedAt: effectiveAt,
        resumeAt: resumeAt,
      );
      return;
    }

    await _notificationService.cancelReminderNotifications();
    await _saveState(
      settled.copyWith(
        activeEngine: ActiveEngine.reminder,
        phase: ReminderPhase.awaitingAction,
        clearReminderStartedAt: true,
        clearNextPreAlertAt: true,
        clearNextReminderAt: true,
      ),
    );
  }

  Future<void> _enterPreAlert({
    required ReminderRuntimeState source,
    required DateTime effectiveAt,
    required bool incrementCounter,
  }) async {
    if (incrementCounter) {
      await _repository.incrementPreAlertCount(at: effectiveAt);
    }

    await _saveState(
      source.copyWith(
        activeEngine: ActiveEngine.reminder,
        phase: ReminderPhase.preAlert,
      ),
    );
  }

  Future<void> _recoverOrEnterBreak({
    required AppSettings settings,
    required ReminderRuntimeState source,
    required DateTime breakStartedAt,
    required DateTime resumeAt,
  }) async {
    final breakEndAt = breakStartedAt.add(settings.restDuration);

    if (!resumeAt.isBefore(breakEndAt)) {
      await _repository.addRestDuration(settings.restDuration, at: breakStartedAt);
      await _repository.incrementCompletedBreakCount(at: breakEndAt);
      if (settings.soundEnabled) {
        await _audioService.playRestEnded();
      }
      await _beginWorkingCycle(settings: settings, startedAt: resumeAt);
      return;
    }

    await _enterResting(
      settings: settings,
      source: source,
      breakStartedAt: breakStartedAt,
      breakEndAt: breakEndAt,
    );
  }

  Future<void> _enterResting({
    required AppSettings settings,
    required ReminderRuntimeState source,
    required DateTime breakStartedAt,
    required DateTime breakEndAt,
  }) async {
    await _notificationService.cancelReminderNotifications();
    await _notificationService.scheduleBreakFinished(
      scheduledAt: breakEndAt,
    );

    await _saveState(
      source.copyWith(
        activeEngine: ActiveEngine.reminder,
        phase: ReminderPhase.resting,
        breakStartedAt: breakStartedAt,
        breakEndAt: breakEndAt,
        clearReminderStartedAt: true,
        clearNextPreAlertAt: true,
        clearNextReminderAt: true,
        clearPausedAt: true,
        clearSuspendedUntil: true,
        isManuallyPaused: false,
      ),
    );
  }

  Future<void> _finishBreak({
    required AppSettings settings,
    required ReminderRuntimeState source,
    required DateTime effectiveAt,
    required DateTime resumeAt,
    bool finishedNaturally = true,
  }) async {
    final endedAt =
        source.breakEndAt != null && effectiveAt.isAfter(source.breakEndAt!)
            ? source.breakEndAt!
            : effectiveAt;

    final elapsed = _boundedDuration(
      startedAt: source.breakStartedAt,
      endedAt: endedAt,
      fallback: settings.restDuration,
    );

    if (elapsed > Duration.zero) {
      await _repository.addRestDuration(elapsed, at: effectiveAt);
    }

    if (finishedNaturally) {
      await _repository.incrementCompletedBreakCount(at: effectiveAt);
    }

    if (settings.soundEnabled) {
      await _audioService.playRestEnded();
    }

    await _beginWorkingCycle(settings: settings, startedAt: resumeAt);
  }

  Future<void> _beginWorkingCycle({
    required AppSettings settings,
    required DateTime startedAt,
  }) async {
    final nextReminderAt = startedAt.add(settings.warnInterval);
    DateTime? nextPreAlertAt;

    if (settings.preAlertEnabled) {
      final candidate = nextReminderAt.subtract(settings.preAlertDuration);
      if (candidate.isAfter(startedAt)) {
        nextPreAlertAt = candidate;
      }
    }

    final nextState = ReminderRuntimeState(
      activeEngine: ActiveEngine.reminder,
      phase: ReminderPhase.working,
      reminderStartedAt: startedAt,
      nextPreAlertAt: nextPreAlertAt,
      nextReminderAt: nextReminderAt,
      isManuallyPaused: false,
      updatedAt: _clockService.now(),
    );

    await _notificationService.cancelReminderNotifications();
    if (nextPreAlertAt != null) {
      await _notificationService.scheduleReminderPreAlert(
        scheduledAt: nextPreAlertAt,
        title: settings.preAlertTitle,
        body: settings.preAlertMessage,
      );
    }
    await _notificationService.scheduleReminderDue(
      scheduledAt: nextReminderAt,
    );

    await _saveState(nextState);
  }

  Future<void> _rescheduleWorkingNotifications({
    required AppSettings settings,
    required ReminderRuntimeState source,
  }) async {
    await _notificationService.cancelReminderNotifications();

    if (source.nextPreAlertAt != null) {
      await _notificationService.scheduleReminderPreAlert(
        scheduledAt: source.nextPreAlertAt!,
        title: settings.preAlertTitle,
        body: settings.preAlertMessage,
      );
    }

    if (source.nextReminderAt != null) {
      await _notificationService.scheduleReminderDue(
        scheduledAt: source.nextReminderAt!,
      );
    }
  }

  Future<ReminderRuntimeState> _settleWorkingCycle({
    required ReminderRuntimeState source,
    required DateTime until,
  }) async {
    if (source.reminderStartedAt == null) {
      return source;
    }

    if (source.phase != ReminderPhase.working &&
        source.phase != ReminderPhase.preAlert) {
      return source;
    }

    final elapsed = until.isAfter(source.reminderStartedAt!)
        ? until.difference(source.reminderStartedAt!)
        : Duration.zero;

    if (elapsed > Duration.zero) {
      await _repository.addWorkingDuration(elapsed, at: until);
    }

    return source.copyWith(
      clearReminderStartedAt: true,
      clearNextPreAlertAt: true,
      clearNextReminderAt: true,
    );
  }

  Future<ReminderRuntimeState> _settleCurrentPhaseForPause(DateTime now) async {
    if (state.phase == ReminderPhase.working ||
        state.phase == ReminderPhase.preAlert) {
      return _settleWorkingCycle(source: state, until: now);
    }

    if (state.phase == ReminderPhase.resting && state.breakStartedAt != null) {
      final elapsed = _boundedDuration(
        startedAt: state.breakStartedAt,
        endedAt: now,
        fallback: Duration.zero,
      );
      if (elapsed > Duration.zero) {
        await _repository.addRestDuration(elapsed, at: now);
      }
    }

    return state;
  }

  Duration _boundedDuration({
    required DateTime? startedAt,
    required DateTime endedAt,
    required Duration fallback,
  }) {
    if (startedAt == null) {
      return fallback;
    }

    if (!endedAt.isAfter(startedAt)) {
      return Duration.zero;
    }

    return endedAt.difference(startedAt);
  }

  Future<void> _saveState(ReminderRuntimeState nextState) async {
    final stateToSave = nextState.copyWith(updatedAt: _clockService.now());
    await _repository.saveRuntimeState(stateToSave);
    state = stateToSave;
  }
}
