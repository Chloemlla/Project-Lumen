import 'package:flutter/material.dart';
import 'package:project_lumen/core/enums/pomodoro_phase.dart';
import 'package:project_lumen/core/enums/reminder_phase.dart';
import 'package:project_lumen/features/pomodoro/domain/models/pomodoro_runtime_state.dart';
import 'package:project_lumen/features/reminder/domain/models/reminder_runtime_state.dart';
import 'package:project_lumen/shared/widgets/app_action_group.dart';

class QuickActionsBar extends StatelessWidget {
  const QuickActionsBar({
    super.key,
    required this.reminderState,
    required this.pomodoroState,
    required this.onStartReminder,
    required this.onPauseReminder,
    required this.onResumeReminder,
    required this.onStartPomodoro,
    required this.onStopPomodoro,
    required this.onOpenBreak,
  });

  final ReminderRuntimeState reminderState;
  final PomodoroRuntimeState pomodoroState;
  final VoidCallback onStartReminder;
  final VoidCallback onPauseReminder;
  final VoidCallback onResumeReminder;
  final VoidCallback onStartPomodoro;
  final VoidCallback onStopPomodoro;
  final VoidCallback onOpenBreak;

  @override
  Widget build(BuildContext context) {
    final reminderAction = switch (reminderState.phase) {
      ReminderPhase.paused => AppAction(
        label: '恢复提醒',
        icon: Icons.play_arrow_rounded,
        onPressed: onResumeReminder,
        style: AppActionStyle.tonal,
      ),
      ReminderPhase.working ||
      ReminderPhase.preAlert ||
      ReminderPhase.awaitingAction ||
      ReminderPhase.resting => AppAction(
        label: '暂停提醒',
        icon: Icons.pause_rounded,
        onPressed: onPauseReminder,
        style: AppActionStyle.tonal,
      ),
      ReminderPhase.idle => AppAction(
        label: '开始提醒',
        icon: Icons.notifications_active_outlined,
        onPressed: onStartReminder,
        style: AppActionStyle.tonal,
      ),
    };

    final pomodoroIsIdle = pomodoroState.phase == PomodoroPhase.idle;
    final pomodoroLabel = pomodoroIsIdle ? '启动番茄' : '查看番茄';

    return AppActionGroup(
      actions: [
        reminderAction,
        AppAction(
          label: pomodoroLabel,
          icon: pomodoroIsIdle ? Icons.timer_outlined : Icons.open_in_new_rounded,
          onPressed: onStartPomodoro,
        ),
        AppAction(
          label: '停止番茄',
          icon: Icons.stop_rounded,
          onPressed: pomodoroIsIdle ? null : onStopPomodoro,
          style: AppActionStyle.outlined,
        ),
        AppAction(
          label: '休息页',
          icon: Icons.self_improvement_rounded,
          onPressed: onOpenBreak,
          style: AppActionStyle.outlined,
        ),
      ],
    );
  }
}
