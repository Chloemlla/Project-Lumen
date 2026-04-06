import 'package:flutter/material.dart';

class BreakActionButtons extends StatelessWidget {
  const BreakActionButtons({
    super.key,
    required this.onStartBreak,
    required this.onSkip,
    required this.disableSkip,
    required this.isResting,
  });

  final VoidCallback onStartBreak;
  final VoidCallback onSkip;
  final bool disableSkip;
  final bool isResting;

  @override
  Widget build(BuildContext context) {
    if (isResting) {
      return FilledButton(onPressed: onStartBreak, child: const Text('提前结束休息'));
    }

    return Wrap(
      spacing: 12,
      runSpacing: 12,
      alignment: WrapAlignment.center,
      children: [
        FilledButton(onPressed: onStartBreak, child: const Text('开始休息')),
        OutlinedButton(
          onPressed: disableSkip ? null : onSkip,
          child: const Text('跳过本次'),
        ),
      ],
    );
  }
}
