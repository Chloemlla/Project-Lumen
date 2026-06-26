import 'dart:async';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:project_lumen/app/app.dart';
import 'package:project_lumen/core/logging/app_logger.dart';
import 'package:project_lumen/core/services/app_lifecycle_service.dart';
import 'package:project_lumen/core/services/audio_service.dart';
import 'package:project_lumen/core/services/background_sync_service.dart';
import 'package:project_lumen/core/services/clock_service.dart';
import 'package:project_lumen/core/services/export_service.dart';
import 'package:project_lumen/core/services/notification_service.dart';
import 'package:project_lumen/core/services/permission_service.dart';
import 'package:project_lumen/core/services/share_service.dart';
import 'package:project_lumen/core/storage/db/app_database.dart';
import 'package:project_lumen/core/storage/prefs/app_prefs.dart';
import 'package:project_lumen/features/pomodoro/domain/repositories/pomodoro_repository.dart';
import 'package:project_lumen/features/reminder/domain/repositories/reminder_repository.dart';
import 'package:project_lumen/features/settings/domain/repositories/settings_repository.dart';
import 'package:project_lumen/features/statistics/domain/repositories/statistics_repository.dart';
import 'package:project_lumen/features/tip_template/domain/repositories/tip_template_repository.dart';
import 'package:shared_preferences/shared_preferences.dart';

final sharedPreferencesProvider = Provider<SharedPreferences>(
  (_) => throw UnimplementedError('SharedPreferences not initialized.'),
);

final appPrefsProvider = Provider<AppPrefs>(
  (_) => throw UnimplementedError('AppPrefs not initialized.'),
);

final appDatabaseProvider = Provider<AppDatabase>(
  (_) => throw UnimplementedError('AppDatabase not initialized.'),
);

final clockServiceProvider = Provider<ClockService>(
  (_) => SystemClockService(),
);

final appLifecycleServiceProvider = Provider<AppLifecycleService>(
  (_) => throw UnimplementedError('AppLifecycleService not initialized.'),
);

final notificationServiceProvider = Provider<AppNotificationService>(
  (_) => throw UnimplementedError('NotificationService not initialized.'),
);

final audioServiceProvider = Provider<AppAudioService>(
  (_) => throw UnimplementedError('AudioService not initialized.'),
);

final exportServiceProvider = Provider<ExportService>((_) => ExportService());

final shareServiceProvider = Provider<ShareService>((_) => ShareService());

final permissionServiceProvider = Provider<PermissionService>(
  (_) => PermissionService(),
);

final backgroundSyncServiceProvider = Provider<BackgroundSyncService>(
  (_) => BackgroundSyncService(),
);

final settingsRepositoryProvider = Provider<SettingsRepository>(
  (_) => throw UnimplementedError('SettingsRepository not initialized.'),
);

final reminderRepositoryProvider = Provider<ReminderRepository>(
  (_) => throw UnimplementedError('ReminderRepository not initialized.'),
);

final pomodoroRepositoryProvider = Provider<PomodoroRepository>(
  (_) => throw UnimplementedError('PomodoroRepository not initialized.'),
);

final statisticsRepositoryProvider = Provider<StatisticsRepository>(
  (_) => throw UnimplementedError('StatisticsRepository not initialized.'),
);

final tipTemplateRepositoryProvider = Provider<TipTemplateRepository>(
  (_) => throw UnimplementedError('TipTemplateRepository not initialized.'),
);

const _bootstrapTimeout = Duration(seconds: 60);

void bootstrap() {
  runZonedGuarded(
    () {
      WidgetsFlutterBinding.ensureInitialized();
      final zone = Zone.current;
      appLogger.info('Flutter binding initialized');
      ErrorWidget.builder = (details) => _BootstrapStatusPage(
        title: '界面加载失败',
        message: details.exceptionAsString(),
        isLoading: false,
        logLines: appLogger.recentLines,
        logFilePath: appLogger.logFilePath,
      );
      FlutterError.onError = (details) {
        FlutterError.presentError(details);
        appLogger.flutterError(details);
        zone.handleUncaughtError(
          details.exception,
          details.stack ?? StackTrace.current,
        );
      };
      PlatformDispatcher.instance.onError = (error, stackTrace) {
        appLogger.error(
          'Uncaught platform dispatcher error',
          error,
          stackTrace,
        );
        _runFatalApp(error, stackTrace);
        return true;
      };
      appLogger.info('Bootstrap app runApp requested');
      runApp(const _BootstrapApp());
    },
    (error, stackTrace) {
      appLogger.error('Uncaught zone error', error, stackTrace);
      runApp(
        MaterialApp(
          debugShowCheckedModeBanner: false,
          home: _BootstrapStatusPage(
            title: '启动失败',
            message: error.toString(),
            isLoading: false,
            logLines: appLogger.recentLines,
            logFilePath: appLogger.logFilePath,
          ),
        ),
      );
    },
  );
}

void _runFatalApp(Object error, StackTrace stackTrace) {
  try {
    runApp(
      MaterialApp(
        debugShowCheckedModeBanner: false,
        home: _BootstrapStatusPage(
          title: 'Startup failed',
          message: error.toString(),
          isLoading: false,
          logLines: appLogger.recentLines,
          logFilePath: appLogger.logFilePath,
        ),
      ),
    );
  } catch (fatalPageError, fatalPageStackTrace) {
    appLogger.error(
      'Failed to render fatal error page',
      fatalPageError,
      fatalPageStackTrace,
    );
  }
}

class _BootstrapApp extends StatefulWidget {
  const _BootstrapApp();

  @override
  State<_BootstrapApp> createState() => _BootstrapAppState();
}

class _BootstrapAppState extends State<_BootstrapApp> {
  late final Future<_BootstrapDependencies> _bootstrapFuture =
      _loadDependencies().timeout(
        _bootstrapTimeout,
        onTimeout: () {
          final error = TimeoutException(
            'Application bootstrap timed out after '
            '${_bootstrapTimeout.inSeconds} seconds.',
          );
          appLogger.error('Bootstrap timeout', error, StackTrace.current, {
            'timeoutSeconds': _bootstrapTimeout.inSeconds,
          });
          throw error;
        },
      );

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<_BootstrapDependencies>(
      future: _bootstrapFuture,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const MaterialApp(
            debugShowCheckedModeBanner: false,
            home: _BootstrapStatusPage(
              title: 'Project-Lumen',
              message: '正在初始化应用...',
            ),
          );
        }

        if (snapshot.hasError) {
          return MaterialApp(
            debugShowCheckedModeBanner: false,
            home: _BootstrapStatusPage(
              title: '启动失败',
              message: snapshot.error.toString(),
              isLoading: false,
              logLines: appLogger.recentLines,
              logFilePath: appLogger.logFilePath,
            ),
          );
        }

        final dependencies = snapshot.requireData;
        return ProviderScope(
          overrides: [
            sharedPreferencesProvider.overrideWithValue(
              dependencies.sharedPreferences,
            ),
            appPrefsProvider.overrideWithValue(dependencies.appPrefs),
            appDatabaseProvider.overrideWithValue(dependencies.appDatabase),
            appLifecycleServiceProvider.overrideWithValue(
              dependencies.lifecycleService,
            ),
            notificationServiceProvider.overrideWithValue(
              dependencies.notificationService,
            ),
            audioServiceProvider.overrideWithValue(dependencies.audioService),
            settingsRepositoryProvider.overrideWithValue(
              dependencies.settingsRepository,
            ),
            reminderRepositoryProvider.overrideWithValue(
              dependencies.reminderRepository,
            ),
            pomodoroRepositoryProvider.overrideWithValue(
              dependencies.pomodoroRepository,
            ),
            statisticsRepositoryProvider.overrideWithValue(
              dependencies.statisticsRepository,
            ),
            tipTemplateRepositoryProvider.overrideWithValue(
              dependencies.tipTemplateRepository,
            ),
          ],
          child: const ProjectLumenApp(),
        );
      },
    );
  }
}

class _BootstrapStatusPage extends StatelessWidget {
  const _BootstrapStatusPage({
    required this.title,
    required this.message,
    this.isLoading = true,
    this.logLines = const [],
    this.logFilePath,
  });

  final String title;
  final String message;
  final bool isLoading;
  final List<String> logLines;
  final String? logFilePath;

  @override
  Widget build(BuildContext context) {
    final copyText = [
      'title: $title',
      'message: $message',
      if (logFilePath != null) 'logFilePath: $logFilePath',
      ...logLines,
    ].join('\n');
    final visibleLogs = logLines.take(80).join('\n');

    return Scaffold(
      backgroundColor: const Color(0xFFF3F7F4),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 900),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    Icons.visibility_rounded,
                    size: 72,
                    color: Theme.of(context).colorScheme.primary,
                  ),
                  const SizedBox(height: 24),
                  if (isLoading) ...[
                    const CircularProgressIndicator(),
                    const SizedBox(height: 20),
                  ],
                  Text(title, style: Theme.of(context).textTheme.headlineSmall),
                  const SizedBox(height: 10),
                  SelectableText(message, textAlign: TextAlign.center),
                  if (!isLoading) ...[
                    const SizedBox(height: 16),
                    FilledButton.icon(
                      onPressed: () async {
                        await Clipboard.setData(ClipboardData(text: copyText));
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text(
                                '\u5df2\u590d\u5236\u5168\u90e8\u65e5\u5fd7',
                              ),
                            ),
                          );
                        }
                      },
                      icon: const Icon(Icons.copy_all_rounded),
                      label: const Text('\u5168\u90e8\u590d\u5236'),
                    ),
                  ],
                  if (logFilePath != null) ...[
                    const SizedBox(height: 16),
                    SelectableText(
                      'Log file: $logFilePath',
                      textAlign: TextAlign.center,
                    ),
                  ],
                  if (visibleLogs.isNotEmpty) ...[
                    const SizedBox(height: 16),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.black87,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: SelectableText(
                        visibleLogs,
                        style: const TextStyle(
                          color: Colors.white,
                          fontFamily: 'monospace',
                          fontSize: 12,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _BootstrapDependencies {
  const _BootstrapDependencies({
    required this.sharedPreferences,
    required this.appPrefs,
    required this.appDatabase,
    required this.lifecycleService,
    required this.notificationService,
    required this.audioService,
    required this.settingsRepository,
    required this.reminderRepository,
    required this.pomodoroRepository,
    required this.statisticsRepository,
    required this.tipTemplateRepository,
  });

  final SharedPreferences sharedPreferences;
  final AppPrefs appPrefs;
  final AppDatabase appDatabase;
  final AppLifecycleService lifecycleService;
  final AppNotificationService notificationService;
  final AppAudioService audioService;
  final SettingsRepository settingsRepository;
  final ReminderRepository reminderRepository;
  final PomodoroRepository pomodoroRepository;
  final StatisticsRepository statisticsRepository;
  final TipTemplateRepository tipTemplateRepository;
}

Future<_BootstrapDependencies> _loadDependencies() async {
  appLogger.info('Bootstrap dependencies loading started');
  final sharedPreferences = await appLogger.trace(
    'SharedPreferences.getInstance',
    SharedPreferences.getInstance,
  );
  appLogger.info('AppPrefs creating');
  final appPrefs = AppPrefs(sharedPreferences);
  final appDatabase = await appLogger.trace(
    'AppDatabase.open',
    AppDatabase.open,
  );
  AppNotificationService notificationService = NoopNotificationService();
  final localNotificationService = LocalNotificationService();
  try {
    await appLogger.trace(
      'LocalNotificationService.initialize',
      localNotificationService.initialize,
    );
    notificationService = localNotificationService;
  } catch (error, stackTrace) {
    appLogger.error(
      'Notification service initialization failed; using noop service',
      error,
      stackTrace,
    );
    notificationService = NoopNotificationService();
  }
  appLogger.info('Audio service creating');
  final audioService = SystemAudioService();
  appLogger.info('Lifecycle service registering');
  final lifecycleService = AppLifecycleService();
  lifecycleService.register();

  appLogger.info('Repositories creating');
  final settingsRepository = LocalSettingsRepository(
    appDatabase.appSettingsDao,
  );
  final reminderRepository = LocalReminderRepository(
    runtimeStateDao: appDatabase.runtimeStateDao,
    dailyEyeStatsDao: appDatabase.dailyEyeStatsDao,
    appSettingsDao: appDatabase.appSettingsDao,
    clockService: SystemClockService(),
  );
  final pomodoroRepository = LocalPomodoroRepository(
    runtimeStateDao: appDatabase.runtimeStateDao,
    dailyPomodoroStatsDao: appDatabase.dailyPomodoroStatsDao,
    appSettingsDao: appDatabase.appSettingsDao,
    clockService: SystemClockService(),
  );
  final statisticsRepository = LocalStatisticsRepository(
    dailyEyeStatsDao: appDatabase.dailyEyeStatsDao,
    dailyPomodoroStatsDao: appDatabase.dailyPomodoroStatsDao,
  );
  final tipTemplateRepository = LocalTipTemplateRepository(
    appDatabase.tipTemplatesDao,
  );

  await appLogger.trace(
    'SettingsRepository.ensureInitialized',
    settingsRepository.ensureInitialized,
  );
  await appLogger.trace(
    'ReminderRepository.ensureInitialized',
    reminderRepository.ensureInitialized,
  );
  await appLogger.trace(
    'PomodoroRepository.ensureInitialized',
    pomodoroRepository.ensureInitialized,
  );
  await appLogger.trace(
    'TipTemplateRepository.seedBuiltIns',
    tipTemplateRepository.seedBuiltIns,
  );

  appLogger.info('Bootstrap dependencies loading completed');
  return _BootstrapDependencies(
    sharedPreferences: sharedPreferences,
    appPrefs: appPrefs,
    appDatabase: appDatabase,
    lifecycleService: lifecycleService,
    notificationService: notificationService,
    audioService: audioService,
    settingsRepository: settingsRepository,
    reminderRepository: reminderRepository,
    pomodoroRepository: pomodoroRepository,
    statisticsRepository: statisticsRepository,
    tipTemplateRepository: tipTemplateRepository,
  );
}
