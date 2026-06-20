import 'dart:async';

import 'package:flutter_local_notifications/flutter_local_notifications.dart';

abstract class AppNotificationService {
  Future<void> scheduleReminderPreAlert({
    required DateTime scheduledAt,
    required String title,
    required String body,
  });

  Future<void> scheduleReminderDue({required DateTime scheduledAt});

  Future<void> scheduleBreakFinished({required DateTime scheduledAt});

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

class NoopNotificationService implements AppNotificationService {
  @override
  Future<void> cancelPomodoroNotifications() async {}

  @override
  Future<void> cancelReminderNotifications() async {}

  @override
  Future<void> scheduleBreakFinished({required DateTime scheduledAt}) async {}

  @override
  Future<void> schedulePomodoroBreakEnd({
    required DateTime scheduledAt,
    required bool isLongBreak,
    required int cycleIndex,
  }) async {}

  @override
  Future<void> schedulePomodoroFocusEnd({
    required DateTime scheduledAt,
    required int cycleIndex,
  }) async {}

  @override
  Future<void> scheduleReminderDue({required DateTime scheduledAt}) async {}

  @override
  Future<void> scheduleReminderPreAlert({
    required DateTime scheduledAt,
    required String title,
    required String body,
  }) async {}
}

class LocalNotificationService implements AppNotificationService {
  LocalNotificationService() : _plugin = FlutterLocalNotificationsPlugin();

  final FlutterLocalNotificationsPlugin _plugin;
  final Map<int, Timer> _timers = <int, Timer>{};

  Future<void> initialize() async {
    const androidSettings = AndroidInitializationSettings(
      '@mipmap/ic_launcher',
    );
    const darwinSettings = DarwinInitializationSettings();
    const settings = InitializationSettings(
      android: androidSettings,
      iOS: darwinSettings,
    );

    await _plugin.initialize(settings: settings);
    await _plugin
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >()
        ?.requestNotificationsPermission();
    await _plugin
        .resolvePlatformSpecificImplementation<
          IOSFlutterLocalNotificationsPlugin
        >()
        ?.requestPermissions(alert: true, badge: true, sound: true);
  }

  Future<void> _showNow({
    required int id,
    required String title,
    required String body,
  }) async {
    const details = NotificationDetails(
      android: AndroidNotificationDetails(
        'project_lumen_default',
        'Project-Lumen',
        channelDescription: 'Project-Lumen default notifications',
        importance: Importance.max,
        priority: Priority.high,
      ),
      iOS: DarwinNotificationDetails(),
    );

    await _plugin.show(
      id: id,
      title: title,
      body: body,
      notificationDetails: details,
    );
  }

  Future<void> _scheduleShow({
    required int id,
    required DateTime scheduledAt,
    required String title,
    required String body,
  }) async {
    _timers.remove(id)?.cancel();
    final delay = scheduledAt.difference(DateTime.now());
    _timers[id] = Timer(delay.isNegative ? Duration.zero : delay, () {
      _timers.remove(id);
      unawaited(_showNow(id: id, title: title, body: body));
    });
  }

  Future<void> _cancelIds(Iterable<int> ids) async {
    for (final id in ids) {
      _timers.remove(id)?.cancel();
      await _plugin.cancel(id: id);
    }
  }

  @override
  Future<void> cancelReminderNotifications() async {
    await _cancelIds(const [1001, 1002, 1003]);
  }

  @override
  Future<void> cancelPomodoroNotifications() async {
    await _cancelIds(const [2001, 2002, 2003]);
  }

  @override
  Future<void> scheduleBreakFinished({required DateTime scheduledAt}) async {
    await _scheduleShow(
      id: 1003,
      scheduledAt: scheduledAt,
      title: '休息即将结束',
      body: 'Project-Lumen 已记录本次休息，返回继续工作。',
    );
  }

  @override
  Future<void> schedulePomodoroBreakEnd({
    required DateTime scheduledAt,
    required bool isLongBreak,
    required int cycleIndex,
  }) async {
    await _scheduleShow(
      id: 2003,
      scheduledAt: scheduledAt,
      title: isLongBreak ? '长休息结束' : '短休息结束',
      body: '第 $cycleIndex 轮已完成，可以进入下一轮专注。',
    );
  }

  @override
  Future<void> schedulePomodoroFocusEnd({
    required DateTime scheduledAt,
    required int cycleIndex,
  }) async {
    await _scheduleShow(
      id: 2002,
      scheduledAt: scheduledAt,
      title: '专注结束',
      body: '第 $cycleIndex 轮专注已结束，请切换到休息阶段。',
    );
  }

  @override
  Future<void> scheduleReminderDue({required DateTime scheduledAt}) async {
    await _scheduleShow(
      id: 1002,
      scheduledAt: scheduledAt,
      title: '该休息了',
      body: 'Project-Lumen 检测到本轮用眼时间已到。',
    );
  }

  @override
  Future<void> scheduleReminderPreAlert({
    required DateTime scheduledAt,
    required String title,
    required String body,
  }) async {
    await _scheduleShow(
      id: 1001,
      scheduledAt: scheduledAt,
      title: title,
      body: body,
    );
  }
}
