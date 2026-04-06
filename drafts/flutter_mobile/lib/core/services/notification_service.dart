abstract class AppNotificationService {
  Future<void> scheduleReminderPreAlert({
    required DateTime scheduledAt,
    required String title,
    required String body,
  });

  Future<void> scheduleReminderDue({
    required DateTime scheduledAt,
  });

  Future<void> scheduleBreakFinished({
    required DateTime scheduledAt,
  });

  Future<void> cancelReminderNotifications();

  Future<void> schedulePomodoroFocusEnd({
    required DateTime scheduledAt,
    required int cycleIndex,
  });

  Future<void> schedulePomodoroBreakEnd({
    required DateTime scheduledAt,
    required bool isLongBreak,
    required int cycleIndex,
  });

  Future<void> cancelPomodoroNotifications();
}
