import 'package:flutter/material.dart';
import 'package:project_lumen/core/utils/duration_x.dart';
import 'package:project_lumen/features/statistics/domain/models/statistics_summary.dart';
import 'package:project_lumen/shared/widgets/app_card.dart';

class TodaySummaryCard extends StatelessWidget {
  const TodaySummaryCard({super.key, required this.summary});

  final StatisticsSummary summary;

  @override
  Widget build(BuildContext context) {
    final items = [
      ('本周工作', Duration(seconds: summary.weeklyWorkingSeconds).compactLabel),
      ('本周休息', Duration(seconds: summary.weeklyRestSeconds).compactLabel),
      ('本周跳过', '${summary.weeklySkipCount} 次'),
      ('本周番茄', '${summary.weeklyTomatoCount} 个'),
    ];

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('今日概览', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 16),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: items
                .map(
                  (item) => SizedBox(
                    width: 140,
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: Theme.of(
                          context,
                        ).colorScheme.primary.withValues(alpha: 0.08),
                        borderRadius: BorderRadius.circular(18),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(14),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              item.$1,
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                            const SizedBox(height: 6),
                            Text(
                              item.$2,
                              style: Theme.of(context).textTheme.titleMedium,
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                )
                .toList(growable: false),
          ),
        ],
      ),
    );
  }
}
