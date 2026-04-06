import 'package:project_eye_mobile/core/enums/active_engine.dart';
import 'package:project_eye_mobile/core/enums/reminder_phase.dart';

class ReminderRuntimeState {
  const ReminderRuntimeState({
    required this.activeEngine,
    required this.phase,
    this.reminderStartedAt,
    this.nextPreAlertAt,
    this.nextReminderAt,
    this.breakStartedAt,
    this.breakEndAt,
    this.isManuallyPaused = false,
    this.pausedAt,
    this.suspendedUntil,
    this.updatedAt,
  });

  const ReminderRuntimeState.idle()
      : activeEngine = ActiveEngine.idle,
        phase = ReminderPhase.idle,
        reminderStartedAt = null,
        nextPreAlertAt = null,
        nextReminderAt = null,
        breakStartedAt = null,
        breakEndAt = null,
        isManuallyPaused = false,
        pausedAt = null,
        suspendedUntil = null,
        updatedAt = null;

  final ActiveEngine activeEngine;
  final ReminderPhase phase;
  final DateTime? reminderStartedAt;
  final DateTime? nextPreAlertAt;
  final DateTime? nextReminderAt;
  final DateTime? breakStartedAt;
  final DateTime? breakEndAt;
  final bool isManuallyPaused;
  final DateTime? pausedAt;
  final DateTime? suspendedUntil;
  final DateTime? updatedAt;

  bool get isIdle => phase == ReminderPhase.idle;

  ReminderRuntimeState copyWith({
    ActiveEngine? activeEngine,
    ReminderPhase? phase,
    DateTime? reminderStartedAt,
    DateTime? nextPreAlertAt,
    DateTime? nextReminderAt,
    DateTime? breakStartedAt,
    DateTime? breakEndAt,
    bool? isManuallyPaused,
    DateTime? pausedAt,
    DateTime? suspendedUntil,
    DateTime? updatedAt,
    bool clearReminderStartedAt = false,
    bool clearNextPreAlertAt = false,
    bool clearNextReminderAt = false,
    bool clearBreakStartedAt = false,
    bool clearBreakEndAt = false,
    bool clearPausedAt = false,
    bool clearSuspendedUntil = false,
  }) {
    return ReminderRuntimeState(
      activeEngine: activeEngine ?? this.activeEngine,
      phase: phase ?? this.phase,
      reminderStartedAt: clearReminderStartedAt
          ? null
          : reminderStartedAt ?? this.reminderStartedAt,
      nextPreAlertAt:
          clearNextPreAlertAt ? null : nextPreAlertAt ?? this.nextPreAlertAt,
      nextReminderAt:
          clearNextReminderAt ? null : nextReminderAt ?? this.nextReminderAt,
      breakStartedAt:
          clearBreakStartedAt ? null : breakStartedAt ?? this.breakStartedAt,
      breakEndAt: clearBreakEndAt ? null : breakEndAt ?? this.breakEndAt,
      isManuallyPaused: isManuallyPaused ?? this.isManuallyPaused,
      pausedAt: clearPausedAt ? null : pausedAt ?? this.pausedAt,
      suspendedUntil: clearSuspendedUntil
          ? null
          : suspendedUntil ?? this.suspendedUntil,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }
}

