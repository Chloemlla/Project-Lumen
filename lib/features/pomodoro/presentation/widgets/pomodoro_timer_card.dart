import 'package:flutter/material.dart';
import 'package:project_lumen/core/utils/duration_x.dart';
import 'package:project_lumen/shared/widgets/app_card.dart';

class PomodoroTimerCard extends StatelessWidget {
  const PomodoroTimerCard({
    super.key,
    required this.title,
    required this.remaining,
  });

  final String title;
  final Duration remaining;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      child: Column(
        children: [
          Text(title, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 12),
          Text(
            remaining.mmssLabel,
            style: Theme.of(context).textTheme.headlineLarge,
          ),
        ],
      ),
    );
  }
}
