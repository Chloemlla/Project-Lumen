enum ReminderAction { startBreak, skipBreak }

extension ReminderActionX on ReminderAction {
  String get storageValue => switch (this) {
    ReminderAction.startBreak => 'start_break',
    ReminderAction.skipBreak => 'skip_break',
  };
}

ReminderAction reminderActionFromStorage(String? value) {
  return ReminderAction.values.firstWhere(
    (action) => action.storageValue == value,
    orElse: () => ReminderAction.startBreak,
  );
}
