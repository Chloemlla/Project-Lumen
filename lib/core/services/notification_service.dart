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

  @override
  Future<void> cancelReminderNotifications() async {
    await _plugin.cancel(id: 1001);
    await _plugin.cancel(id: 1002);
    await _plugin.cancel(id: 1003);
  }

  @override
  Future<void> cancelPomodoroNotifications() async {
    await _plugin.cancel(id: 2001);
    await _plugin.cancel(id: 2002);
    await _plugin.cancel(id: 2003);
  }

  @override
  Future<void> scheduleBreakFinished({required DateTime scheduledAt}) async {
    await _showNow(
      id: 1003,
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
    await _showNow(
      id: 2003,
      title: isLongBreak ? '长休息结束' : '短休息结束',
      body: '第 $cycleIndex 轮已完成，可以进入下一轮专注。',
    );
  }

  @override
  Future<void> schedulePomodoroFocusEnd({
    required DateTime scheduledAt,
    required int cycleIndex,
  }) async {
    await _showNow(
      id: 2002,
      title: '专注结束',
      body: '第 $cycleIndex 轮专注已结束，请切换到休息阶段。',
    );
  }

  @override
  Future<void> scheduleReminderDue({required DateTime scheduledAt}) async {
    await _showNow(id: 1002, title: '该休息了', body: 'Project-Lumen 检测到本轮用眼时间已到。');
  }

  @override
  Future<void> scheduleReminderPreAlert({
    required DateTime scheduledAt,
    required String title,
    required String body,
  }) async {
    await _showNow(id: 1001, title: title, body: body);
  }
}
