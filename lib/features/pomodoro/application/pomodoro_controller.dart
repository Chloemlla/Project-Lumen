import 'package:flutter_riverpod/legacy.dart';
import 'package:project_lumen/app/bootstrap.dart';
import 'package:project_lumen/core/enums/active_engine.dart';
import 'package:project_lumen/core/enums/pomodoro_phase.dart';
import 'package:project_lumen/core/services/audio_service.dart';
import 'package:project_lumen/core/services/clock_service.dart';
import 'package:project_lumen/core/services/notification_service.dart';
import 'package:project_lumen/features/pomodoro/domain/models/pomodoro_runtime_state.dart';
import 'package:project_lumen/features/pomodoro/domain/repositories/pomodoro_repository.dart';
import 'package:project_lumen/features/settings/domain/models/app_settings.dart';

final pomodoroControllerProvider =
    StateNotifierProvider<PomodoroController, PomodoroRuntimeState>((ref) {
      final controller = PomodoroController(
        repository: ref.watch(pomodoroRepositoryProvider),
        notificationService: ref.watch(notificationServiceProvider),
        audioService: ref.watch(audioServiceProvider),
        clockService: ref.watch(clockServiceProvider),
      );
      controller.hydrate();
      return controller;
    });

class PomodoroController extends StateNotifier<PomodoroRuntimeState> {
  PomodoroController({
    required PomodoroRepository repository,
    required AppNotificationService notificationService,
    required AppAudioService audioService,
    required ClockService clockService,
  }) : _repository = repository,
       _notificationService = notificationService,
       _audioService = audioService,
       _clockService = clockService,
       super(const PomodoroRuntimeState.idle());

  final PomodoroRepository _repository;
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

    if (settings.pomodoroInteractiveMode) {
      await _saveState(
        PomodoroRuntimeState(
          activeEngine: ActiveEngine.pomodoro,
          phase: PomodoroPhase.awaitingFocusConfirm,
          cycleIndex: 1,
          updatedAt: now,
        ),
      );
      return;
    }

    await _enterFocusPhase(settings: settings, cycleIndex: 1, startedAt: now);
  }

  Future<void> confirmFocusStart() async {
    final settings = await _repository.getSettings();
    await _enterFocusPhase(
      settings: settings,
      cycleIndex: state.cycleIndex < 1 ? 1 : state.cycleIndex,
      startedAt: _clockService.now(),
    );
  }

  Future<void> handlePhaseEnd() async {
    final settings = await _repository.getSettings();
    final now = _clockService.now();

    if (state.phase == PomodoroPhase.focus) {
      await _finishFocus(
        settings: settings,
        source: state,
        effectiveAt: state.phaseEndAt ?? now,
        resumeAt: now,
      );
      return;
    }

    if (state.phase == PomodoroPhase.shortBreak ||
        state.phase == PomodoroPhase.longBreak) {
      await _finishBreak(
        settings: settings,
        source: state,
        effectiveAt: state.phaseEndAt ?? now,
        resumeAt: now,
      );
    }
  }

  Future<void> skipBreak() async {
    if (state.phase != PomodoroPhase.shortBreak &&
        state.phase != PomodoroPhase.longBreak) {
      return;
    }

    final settings = await _repository.getSettings();
    final now = _clockService.now();
    await _finishBreak(
      settings: settings,
      source: state,
      effectiveAt: now,
      resumeAt: now,
      finishedNaturally: false,
    );
  }

  Future<void> stop({bool countAsRestart = true}) async {
    if (countAsRestart &&
        state.activeEngine == ActiveEngine.pomodoro &&
        state.phase != PomodoroPhase.idle) {
      await _repository.incrementRestartCount(at: _clockService.now());
    }

    await _notificationService.cancelPomodoroNotifications();
    await _saveState(const PomodoroRuntimeState.idle());
  }

  Future<void> recover() async {
    final settings = await _repository.getSettings();
    final saved = await _repository.getRuntimeState();
    final now = _clockService.now();
    state = saved;

    if (saved.activeEngine != ActiveEngine.pomodoro ||
        saved.phase == PomodoroPhase.idle) {
      return;
    }

    if (saved.phase == PomodoroPhase.awaitingFocusConfirm) {
      return;
    }

    final phaseEndAt = saved.phaseEndAt;
    if (phaseEndAt == null) {
      await start();
      return;
    }

    if (!now.isBefore(phaseEndAt)) {
      if (saved.phase == PomodoroPhase.focus) {
        await _finishFocus(
          settings: settings,
          source: saved,
          effectiveAt: phaseEndAt,
          resumeAt: now,
        );
      } else {
        await _finishBreak(
          settings: settings,
          source: saved,
          effectiveAt: phaseEndAt,
          resumeAt: now,
        );
      }
      return;
    }

    await _notificationService.cancelPomodoroNotifications();
    if (saved.phase == PomodoroPhase.focus) {
      await _notificationService.schedulePomodoroFocusEnd(
        scheduledAt: phaseEndAt,
        cycleIndex: saved.cycleIndex,
      );
    } else {
      await _notificationService.schedulePomodoroBreakEnd(
        scheduledAt: phaseEndAt,
        isLongBreak: saved.phase == PomodoroPhase.longBreak,
        cycleIndex: saved.cycleIndex,
      );
    }
  }

  Future<void> _enterFocusPhase({
    required AppSettings settings,
    required int cycleIndex,
    required DateTime startedAt,
  }) async {
    final phaseEndAt = startedAt.add(settings.pomodoroWorkDuration);

    await _notificationService.cancelPomodoroNotifications();
    await _notificationService.schedulePomodoroFocusEnd(
      scheduledAt: phaseEndAt,
      cycleIndex: cycleIndex,
    );

    if (settings.pomodoroWorkStartSoundEnabled) {
      await _audioService.playPomodoroWorkStart();
    }

    await _saveState(
      PomodoroRuntimeState(
        activeEngine: ActiveEngine.pomodoro,
        phase: PomodoroPhase.focus,
        cycleIndex: cycleIndex,
        phaseStartedAt: startedAt,
        phaseEndAt: phaseEndAt,
      ),
    );
  }

  Future<void> _finishFocus({
    required AppSettings settings,
    required PomodoroRuntimeState source,
    required DateTime effectiveAt,
    required DateTime resumeAt,
  }) async {
    final elapsed = _boundedDuration(
      startedAt: source.phaseStartedAt,
      endedAt: effectiveAt,
      fallback: settings.pomodoroWorkDuration,
    );

    await _repository.addFocusDuration(elapsed, at: effectiveAt);
    await _repository.incrementCompletedFocusSessions(at: effectiveAt);

    if (settings.pomodoroWorkEndSoundEnabled) {
      await _audioService.playPomodoroWorkEnd();
    }

    final isLongBreak = source.cycleIndex >= 4;
    if (isLongBreak) {
      await _repository.incrementCompletedTomatoCount(at: effectiveAt);
    }

    final breakDuration = isLongBreak
        ? settings.pomodoroLongBreakDuration
        : settings.pomodoroShortBreakDuration;

    final breakPhase = isLongBreak
        ? PomodoroPhase.longBreak
        : PomodoroPhase.shortBreak;
    final breakEndAt = effectiveAt.add(breakDuration);

    if (!resumeAt.isBefore(breakEndAt)) {
      await _finishBreak(
        settings: settings,
        source: source.copyWith(
          phase: breakPhase,
          phaseStartedAt: effectiveAt,
          phaseEndAt: breakEndAt,
        ),
        effectiveAt: breakEndAt,
        resumeAt: resumeAt,
      );
      return;
    }

    await _notificationService.cancelPomodoroNotifications();
    await _notificationService.schedulePomodoroBreakEnd(
      scheduledAt: breakEndAt,
      isLongBreak: isLongBreak,
      cycleIndex: source.cycleIndex,
    );

    await _saveState(
      source.copyWith(
        activeEngine: ActiveEngine.pomodoro,
        phase: breakPhase,
        phaseStartedAt: effectiveAt,
        phaseEndAt: breakEndAt,
      ),
    );
  }

  Future<void> _finishBreak({
    required AppSettings settings,
    required PomodoroRuntimeState source,
    required DateTime effectiveAt,
    required DateTime resumeAt,
    bool finishedNaturally = true,
  }) async {
    final expectedDuration = source.phase == PomodoroPhase.longBreak
        ? settings.pomodoroLongBreakDuration
        : settings.pomodoroShortBreakDuration;

    final elapsed = _boundedDuration(
      startedAt: source.phaseStartedAt,
      endedAt: effectiveAt,
      fallback: expectedDuration,
    );

    if (finishedNaturally || elapsed > Duration.zero) {
      await _repository.addBreakDuration(elapsed, at: effectiveAt);
    }

    final nextCycleIndex = source.phase == PomodoroPhase.longBreak
        ? 1
        : source.cycleIndex + 1;

    if (settings.pomodoroInteractiveMode) {
      await _notificationService.cancelPomodoroNotifications();
      await _saveState(
        PomodoroRuntimeState(
          activeEngine: ActiveEngine.pomodoro,
          phase: PomodoroPhase.awaitingFocusConfirm,
          cycleIndex: nextCycleIndex,
          updatedAt: resumeAt,
        ),
      );
      return;
    }

    await _enterFocusPhase(
      settings: settings,
      cycleIndex: nextCycleIndex,
      startedAt: resumeAt,
    );
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

  Future<void> _saveState(PomodoroRuntimeState nextState) async {
    final stateToSave = nextState.copyWith(updatedAt: _clockService.now());
    await _repository.saveRuntimeState(stateToSave);
    state = stateToSave;
  }
}
