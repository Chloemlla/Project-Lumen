import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:project_lumen/features/home/presentation/widgets/next_reminder_card.dart';
import 'package:project_lumen/features/home/presentation/widgets/quick_actions_bar.dart';
import 'package:project_lumen/features/home/presentation/widgets/today_summary_card.dart';
import 'package:project_lumen/features/pomodoro/application/pomodoro_controller.dart';
import 'package:project_lumen/features/pomodoro/domain/models/pomodoro_runtime_state.dart';
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
    final pomodoroState = ref.watch(pomodoroControllerProvider);
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
            _HomeContent(
              summary: summary,
              reminderState: reminderState,
              pomodoroState: pomodoroState,
            ),
        error: (_, _) => _HomeContent(
          summary: const StatisticsSummary.empty(),
          reminderState: reminderState,
          pomodoroState: pomodoroState,
        ),
        loading: () => const AppLoadingView(),
      ),
    );
  }
}

class _HomeContent extends ConsumerWidget {
  const _HomeContent({
    required this.summary,
    required this.reminderState,
    required this.pomodoroState,
  });

  final StatisticsSummary summary;
  final ReminderRuntimeState reminderState;
  final PomodoroRuntimeState pomodoroState;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ListView(
      children: [
        _HomeHeroPanel(
          onStartReminder: () {
            ref.read(reminderControllerProvider.notifier).start();
          },
          onStartPomodoro: () {
            ref.read(pomodoroControllerProvider.notifier).start();
            context.go('/pomodoro');
          },
        ),
        const SizedBox(height: 16),
        TodaySummaryCard(summary: summary),
        const SizedBox(height: 16),
        NextReminderCard(state: reminderState),
        const SizedBox(height: 16),
        QuickActionsBar(
          reminderState: reminderState,
          pomodoroState: pomodoroState,
          onStartReminder: () {
            ref.read(reminderControllerProvider.notifier).start();
          },
          onPauseReminder: () {
            ref.read(reminderControllerProvider.notifier).pauseUntil(null);
          },
          onResumeReminder: () {
            ref.read(reminderControllerProvider.notifier).resume();
          },
          onStartPomodoro: () {
            if (pomodoroState.isIdle) {
              ref.read(pomodoroControllerProvider.notifier).start();
            }
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

class _HomeHeroPanel extends StatelessWidget {
  const _HomeHeroPanel({
    required this.onStartReminder,
    required this.onStartPomodoro,
  });

  final VoidCallback onStartReminder;
  final VoidCallback onStartPomodoro;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return DecoratedBox(
      decoration: BoxDecoration(
        color: colorScheme.primary,
        borderRadius: BorderRadius.circular(24),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(18),
              child: Image.asset(
                'assets/icon/icon.png',
                width: 72,
                height: 72,
                fit: BoxFit.cover,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Project-Lumen',
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      color: colorScheme.onPrimary,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    '护眼提醒与番茄专注',
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: colorScheme.onPrimary.withValues(alpha: 0.82),
                    ),
                  ),
                  const SizedBox(height: 14),
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: [
                      FilledButton.tonal(
                        onPressed: onStartReminder,
                        child: const Text('开始提醒'),
                      ),
                      OutlinedButton(
                        onPressed: onStartPomodoro,
                        style: OutlinedButton.styleFrom(
                          foregroundColor: colorScheme.onPrimary,
                          side: BorderSide(
                            color: colorScheme.onPrimary.withValues(
                              alpha: 0.7,
                            ),
                          ),
                        ),
                        child: const Text('开始专注'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
