import 'package:flutter/material.dart';

class QuickActionsBar extends StatelessWidget {
  const QuickActionsBar({
    super.key,
    required this.onStartReminder,
    required this.onPauseReminder,
    required this.onStartPomodoro,
    required this.onStopPomodoro,
    required this.onOpenBreak,
  });

  final VoidCallback onStartReminder;
  final VoidCallback onPauseReminder;
  final VoidCallback onStartPomodoro;
  final VoidCallback onStopPomodoro;
  final VoidCallback onOpenBreak;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 12,
      runSpacing: 12,
      children: [
        FilledButton.tonal(
          onPressed: onStartReminder,
          child: const Text('开始提醒'),
        ),
        FilledButton.tonal(
          onPressed: onPauseReminder,
          child: const Text('暂停提醒'),
        ),
        FilledButton(onPressed: onStartPomodoro, child: const Text('启动番茄')),
        OutlinedButton(onPressed: onStopPomodoro, child: const Text('停止番茄')),
        OutlinedButton(onPressed: onOpenBreak, child: const Text('进入休息页')),
      ],
    );
  }
}
