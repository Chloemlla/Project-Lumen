import 'package:flutter/material.dart';
import 'package:project_lumen/core/enums/reminder_phase.dart';
import 'package:project_lumen/core/utils/duration_x.dart';
import 'package:project_lumen/features/reminder/domain/models/reminder_runtime_state.dart';
import 'package:project_lumen/shared/widgets/app_card.dart';

class NextReminderCard extends StatelessWidget {
  const NextReminderCard({super.key, required this.state});

  final ReminderRuntimeState state;

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final nextReminderAt = state.nextReminderAt;
    final remaining = nextReminderAt == null || nextReminderAt.isBefore(now)
        ? Duration.zero
        : nextReminderAt.difference(now);

    final status = switch (state.phase) {
      ReminderPhase.working => '工作中',
      ReminderPhase.preAlert => '预提醒',
      ReminderPhase.awaitingAction => '等待选择',
      ReminderPhase.resting => '休息中',
      ReminderPhase.paused => '已暂停',
      ReminderPhase.idle => '未启动',
    };

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('下一次提醒', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 10),
          Text(status, style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: 8),
          Text(
            nextReminderAt == null ? '尚未安排' : '剩余 ${remaining.compactLabel}',
            style: Theme.of(context).textTheme.headlineMedium,
          ),
        ],
      ),
    );
  }
}
