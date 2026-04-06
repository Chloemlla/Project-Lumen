import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:project_lumen/app/app.dart';
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

void bootstrap() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const _BootstrapApp());
}

class _BootstrapApp extends StatefulWidget {
  const _BootstrapApp();

  @override
  State<_BootstrapApp> createState() => _BootstrapAppState();
}

class _BootstrapAppState extends State<_BootstrapApp> {
  late final Future<_BootstrapDependencies> _bootstrapFuture =
      _loadDependencies();

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
            ),
          );
        }

        final dependencies = snapshot.requireData;
        return ProviderScope(
          overrides: dependencies.overrides,
          child: const ProjectLumenApp(),
        );
      },
    );
  }
}

class _BootstrapStatusPage extends StatelessWidget {
  const _BootstrapStatusPage({required this.title, required this.message});

  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const CircularProgressIndicator(),
              const SizedBox(height: 20),
              Text(title, style: Theme.of(context).textTheme.headlineSmall),
              const SizedBox(height: 10),
              Text(message, textAlign: TextAlign.center),
            ],
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

  get overrides => [
    sharedPreferencesProvider.overrideWithValue(sharedPreferences),
    appPrefsProvider.overrideWithValue(appPrefs),
    appDatabaseProvider.overrideWithValue(appDatabase),
    appLifecycleServiceProvider.overrideWithValue(lifecycleService),
    notificationServiceProvider.overrideWithValue(notificationService),
    audioServiceProvider.overrideWithValue(audioService),
    settingsRepositoryProvider.overrideWithValue(settingsRepository),
    reminderRepositoryProvider.overrideWithValue(reminderRepository),
    pomodoroRepositoryProvider.overrideWithValue(pomodoroRepository),
    statisticsRepositoryProvider.overrideWithValue(statisticsRepository),
    tipTemplateRepositoryProvider.overrideWithValue(tipTemplateRepository),
  ];
}

Future<_BootstrapDependencies> _loadDependencies() async {
  final sharedPreferences = await SharedPreferences.getInstance();
  final appPrefs = AppPrefs(sharedPreferences);
  final appDatabase = await AppDatabase.open();
  AppNotificationService notificationService = NoopNotificationService();
  final localNotificationService = LocalNotificationService();
  try {
    await localNotificationService.initialize();
    notificationService = localNotificationService;
  } catch (_) {
    notificationService = NoopNotificationService();
  }
  final audioService = SilentAudioService();
  final lifecycleService = AppLifecycleService();
  lifecycleService.register();

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

  await settingsRepository.ensureInitialized();
  await reminderRepository.ensureInitialized();
  await pomodoroRepository.ensureInitialized();
  await tipTemplateRepository.seedBuiltIns();

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
