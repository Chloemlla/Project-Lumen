import 'package:flutter/material.dart';

class PomodoroCycleBar extends StatelessWidget {
  const PomodoroCycleBar({super.key, required this.cycleIndex});

  final int cycleIndex;

  @override
  Widget build(BuildContext context) {
    final progress = cycleIndex.clamp(0, 4) / 4;

    return ClipRRect(
      borderRadius: BorderRadius.circular(4),
      child: LinearProgressIndicator(
        value: progress,
        minHeight: 8,
        backgroundColor: Theme.of(
          context,
        ).colorScheme.primary.withValues(alpha: 0.14),
      ),
    );
  }
}
