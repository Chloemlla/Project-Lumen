import 'package:flutter/material.dart';
import 'package:project_lumen/core/utils/duration_x.dart';

class CountdownRing extends StatelessWidget {
  const CountdownRing({
    super.key,
    required this.remaining,
    required this.total,
  });

  final Duration remaining;
  final Duration total;

  @override
  Widget build(BuildContext context) {
    final progress = total.inSeconds <= 0
        ? 0.0
        : (1 - (remaining.inSeconds / total.inSeconds).clamp(0, 1)).toDouble();

    return SizedBox(
      width: 220,
      height: 220,
      child: Stack(
        alignment: Alignment.center,
        children: [
          SizedBox.expand(
            child: CircularProgressIndicator(
              value: progress,
              strokeWidth: 14,
              backgroundColor: Theme.of(
                context,
              ).colorScheme.primary.withValues(alpha: 0.15),
            ),
          ),
          Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                remaining.mmssLabel,
                style: Theme.of(context).textTheme.headlineLarge,
              ),
              const SizedBox(height: 8),
              Text(
                '保持离屏，稍后自动恢复',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ],
          ),
        ],
      ),
    );
  }
}
