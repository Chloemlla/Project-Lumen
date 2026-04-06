import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:project_lumen/app/l10n/l10n.dart';
import 'package:project_lumen/app/router/app_router.dart';
import 'package:project_lumen/app/theme/app_theme.dart';
import 'package:project_lumen/core/enums/app_theme_mode.dart';
import 'package:project_lumen/features/settings/application/settings_controller.dart';

class ProjectLumenApp extends ConsumerWidget {
  const ProjectLumenApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsControllerProvider);
    final router = ref.watch(appRouterProvider);

    return MaterialApp.router(
      title: AppL10n.of(settings.languageCode).appName,
      debugShowCheckedModeBanner: false,
      themeMode: settings.themeMode.themeMode,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      routerConfig: router,
    );
  }
}
