import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:project_lumen/app/bootstrap.dart';
import 'package:project_lumen/app/router/route_names.dart';
import 'package:project_lumen/features/about/presentation/pages/about_page.dart';
import 'package:project_lumen/features/home/presentation/pages/home_page.dart';
import 'package:project_lumen/features/pomodoro/presentation/pages/pomodoro_page.dart';
import 'package:project_lumen/features/reminder/presentation/pages/break_page.dart';
import 'package:project_lumen/features/settings/presentation/pages/settings_page.dart';
import 'package:project_lumen/features/statistics/presentation/pages/statistics_page.dart';
import 'package:project_lumen/features/tip_template/presentation/pages/tip_template_page.dart';
import 'package:project_lumen/features/tip_template/presentation/pages/tip_template_preview_page.dart';

final appRouterProvider = Provider<GoRouter>((ref) {
  final initialLocation = ref.watch(appPrefsProvider).getLastRoute() ?? '/';

  return GoRouter(
    initialLocation: initialLocation,
    routes: [
      GoRoute(
        path: '/',
        name: RouteNames.home,
        builder: (context, state) => const HomePage(),
      ),
      GoRoute(
        path: '/break',
        name: RouteNames.breakPage,
        builder: (context, state) => const BreakPage(),
      ),
      GoRoute(
        path: '/pomodoro',
        name: RouteNames.pomodoro,
        builder: (context, state) => const PomodoroPage(),
      ),
      GoRoute(
        path: '/statistics',
        name: RouteNames.statistics,
        builder: (context, state) => const StatisticsPage(),
      ),
      GoRoute(
        path: '/settings',
        name: RouteNames.settings,
        builder: (context, state) => const SettingsPage(),
      ),
      GoRoute(
        path: '/templates',
        name: RouteNames.tipTemplates,
        builder: (context, state) => const TipTemplatePage(),
      ),
      GoRoute(
        path: '/templates/preview',
        name: RouteNames.tipTemplatePreview,
        builder: (context, state) => const TipTemplatePreviewPage(),
      ),
      GoRoute(
        path: '/about',
        name: RouteNames.about,
        builder: (context, state) => const AboutPage(),
      ),
    ],
  );
});
