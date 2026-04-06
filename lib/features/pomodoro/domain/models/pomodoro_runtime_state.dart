import 'package:project_lumen/core/enums/active_engine.dart';
import 'package:project_lumen/core/enums/pomodoro_phase.dart';

class PomodoroRuntimeState {
  const PomodoroRuntimeState({
    required this.activeEngine,
    required this.phase,
    required this.cycleIndex,
    this.phaseStartedAt,
    this.phaseEndAt,
    this.isManuallyPaused = false,
    this.pausedAt,
    this.suspendedUntil,
    this.updatedAt,
  });

  const PomodoroRuntimeState.idle()
    : activeEngine = ActiveEngine.idle,
      phase = PomodoroPhase.idle,
      cycleIndex = 1,
      phaseStartedAt = null,
      phaseEndAt = null,
      isManuallyPaused = false,
      pausedAt = null,
      suspendedUntil = null,
      updatedAt = null;

  final ActiveEngine activeEngine;
  final PomodoroPhase phase;
  final int cycleIndex;
  final DateTime? phaseStartedAt;
  final DateTime? phaseEndAt;
  final bool isManuallyPaused;
  final DateTime? pausedAt;
  final DateTime? suspendedUntil;
  final DateTime? updatedAt;

  bool get isIdle => phase == PomodoroPhase.idle;

  bool get isBreak =>
      phase == PomodoroPhase.shortBreak || phase == PomodoroPhase.longBreak;

  PomodoroRuntimeState copyWith({
    ActiveEngine? activeEngine,
    PomodoroPhase? phase,
    int? cycleIndex,
    DateTime? phaseStartedAt,
    DateTime? phaseEndAt,
    bool? isManuallyPaused,
    DateTime? pausedAt,
    DateTime? suspendedUntil,
    DateTime? updatedAt,
    bool clearPhaseStartedAt = false,
    bool clearPhaseEndAt = false,
    bool clearPausedAt = false,
    bool clearSuspendedUntil = false,
  }) {
    return PomodoroRuntimeState(
      activeEngine: activeEngine ?? this.activeEngine,
      phase: phase ?? this.phase,
      cycleIndex: cycleIndex ?? this.cycleIndex,
      phaseStartedAt: clearPhaseStartedAt
          ? null
          : phaseStartedAt ?? this.phaseStartedAt,
      phaseEndAt: clearPhaseEndAt ? null : phaseEndAt ?? this.phaseEndAt,
      isManuallyPaused: isManuallyPaused ?? this.isManuallyPaused,
      pausedAt: clearPausedAt ? null : pausedAt ?? this.pausedAt,
      suspendedUntil: clearSuspendedUntil
          ? null
          : suspendedUntil ?? this.suspendedUntil,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }
}
