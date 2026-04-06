import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:project_lumen/core/enums/reminder_phase.dart';
import 'package:project_lumen/features/reminder/application/reminder_controller.dart';
import 'package:project_lumen/features/reminder/presentation/widgets/break_action_buttons.dart';
import 'package:project_lumen/features/reminder/presentation/widgets/countdown_ring.dart';
import 'package:project_lumen/features/settings/application/settings_controller.dart';
import 'package:project_lumen/shared/widgets/app_card.dart';
import 'package:project_lumen/shared/widgets/app_empty_view.dart';
import 'package:project_lumen/shared/widgets/app_scaffold.dart';

class BreakPage extends ConsumerWidget {
  const BreakPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final reminderState = ref.watch(reminderControllerProvider);
    final settings = ref.watch(settingsControllerProvider);

    if (reminderState.phase == ReminderPhase.idle) {
      return const AppScaffold(
        title: '休息页',
        location: '/break',
        body: AppEmptyView(
          title: '当前没有休息任务',
          message: '你可以从首页启动普通提醒，或直接进入番茄模式。',
        ),
      );
    }

    return AppScaffold(
      title: '休息页',
      location: '/break',
      body: StreamBuilder<int>(
        stream: Stream<int>.periodic(
          const Duration(seconds: 1),
          (value) => value,
        ).asBroadcastStream(),
        builder: (context, snapshot) {
          final now = DateTime.now();
          final remaining = reminderState.breakEndAt == null
              ? settings.restDuration
              : reminderState.breakEndAt!.difference(now);
          final safeRemaining = remaining.isNegative
              ? Duration.zero
              : remaining;

          return ListView(
            children: [
              AppCard(
                child: Column(
                  children: [
                    Text(
                      reminderState.phase == ReminderPhase.resting
                          ? '正在休息'
                          : '该离开屏幕了',
                      style: Theme.of(context).textTheme.headlineMedium,
                    ),
                    const SizedBox(height: 12),
                    Text(settings.preAlertMessage, textAlign: TextAlign.center),
                    const SizedBox(height: 24),
                    CountdownRing(
                      remaining: safeRemaining,
                      total: settings.restDuration,
                    ),
                    const SizedBox(height: 24),
                    BreakActionButtons(
                      onStartBreak: () {
                        if (reminderState.phase == ReminderPhase.resting) {
                          ref
                              .read(reminderControllerProvider.notifier)
                              .completeBreak(finishedNaturally: false);
                          return;
                        }
                        ref
                            .read(reminderControllerProvider.notifier)
                            .startBreak();
                      },
                      onSkip: () {
                        ref
                            .read(reminderControllerProvider.notifier)
                            .skipCurrentBreak();
                      },
                      disableSkip: settings.disableSkip,
                      isResting: reminderState.phase == ReminderPhase.resting,
                    ),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
