enum PomodoroPhase { idle, awaitingFocusConfirm, focus, shortBreak, longBreak }

extension PomodoroPhaseX on PomodoroPhase {
  String get storageValue => switch (this) {
    PomodoroPhase.idle => 'idle',
    PomodoroPhase.awaitingFocusConfirm => 'awaiting_focus_confirm',
    PomodoroPhase.focus => 'focus',
    PomodoroPhase.shortBreak => 'short_break',
    PomodoroPhase.longBreak => 'long_break',
  };
}

PomodoroPhase pomodoroPhaseFromStorage(String? value) {
  return PomodoroPhase.values.firstWhere(
    (phase) => phase.storageValue == value,
    orElse: () => PomodoroPhase.idle,
  );
}
