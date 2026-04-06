import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:project_lumen/features/home/presentation/widgets/next_reminder_card.dart';
import 'package:project_lumen/features/home/presentation/widgets/quick_actions_bar.dart';
import 'package:project_lumen/features/home/presentation/widgets/today_summary_card.dart';
import 'package:project_lumen/features/pomodoro/application/pomodoro_controller.dart';
import 'package:project_lumen/features/reminder/application/reminder_controller.dart';
import 'package:project_lumen/features/reminder/domain/models/reminder_runtime_state.dart';
import 'package:project_lumen/features/statistics/application/statistics_service.dart';
import 'package:project_lumen/features/statistics/domain/models/statistics_summary.dart';
import 'package:project_lumen/shared/widgets/app_loading_view.dart';
import 'package:project_lumen/shared/widgets/app_scaffold.dart';

class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final reminderState = ref.watch(reminderControllerProvider);
    final summaryValue = ref.watch(statisticsSummaryProvider);

    return AppScaffold(
      title: 'Project-Lumen',
      location: '/',
      actions: [
        IconButton(
          onPressed: () => context.go('/about'),
          icon: const Icon(Icons.info_outline_rounded),
        ),
      ],
      body: summaryValue.when(
        data: (summary) =>
            _HomeContent(summary: summary, reminderState: reminderState),
        error: (_, _) => _HomeContent(
          summary: const StatisticsSummary.empty(),
          reminderState: reminderState,
        ),
        loading: () => const AppLoadingView(),
      ),
    );
  }
}

class _HomeContent extends ConsumerWidget {
  const _HomeContent({required this.summary, required this.reminderState});

  final StatisticsSummary summary;
  final ReminderRuntimeState reminderState;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ListView(
      children: [
        TodaySummaryCard(summary: summary),
        const SizedBox(height: 16),
        NextReminderCard(state: reminderState),
        const SizedBox(height: 16),
        QuickActionsBar(
          onStartReminder: () {
            ref.read(reminderControllerProvider.notifier).start();
          },
          onPauseReminder: () {
            ref.read(reminderControllerProvider.notifier).pauseUntil(null);
          },
          onStartPomodoro: () {
            ref.read(pomodoroControllerProvider.notifier).start();
            context.go('/pomodoro');
          },
          onStopPomodoro: () {
            ref.read(pomodoroControllerProvider.notifier).stop();
          },
          onOpenBreak: () => context.go('/break'),
        ),
      ],
    );
  }
}
