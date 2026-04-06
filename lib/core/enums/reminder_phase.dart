enum ReminderPhase { idle, working, preAlert, awaitingAction, resting, paused }

extension ReminderPhaseX on ReminderPhase {
  String get storageValue => switch (this) {
    ReminderPhase.idle => 'idle',
    ReminderPhase.working => 'working',
    ReminderPhase.preAlert => 'pre_alert',
    ReminderPhase.awaitingAction => 'awaiting_action',
    ReminderPhase.resting => 'resting',
    ReminderPhase.paused => 'paused',
  };
}

ReminderPhase reminderPhaseFromStorage(String? value) {
  return ReminderPhase.values.firstWhere(
    (phase) => phase.storageValue == value,
    orElse: () => ReminderPhase.idle,
  );
}
