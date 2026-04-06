import 'package:flutter/material.dart';
import 'package:project_lumen/core/utils/duration_x.dart';
import 'package:project_lumen/features/statistics/domain/models/statistics_summary.dart';
import 'package:project_lumen/shared/widgets/app_card.dart';

class WeeklySummaryCards extends StatelessWidget {
  const WeeklySummaryCards({super.key, required this.summary});

  final StatisticsSummary summary;

  @override
  Widget build(BuildContext context) {
    final items = [
      ('工作', Duration(seconds: summary.weeklyWorkingSeconds).compactLabel),
      ('休息', Duration(seconds: summary.weeklyRestSeconds).compactLabel),
      ('跳过', '${summary.weeklySkipCount} 次'),
      ('番茄', '${summary.weeklyTomatoCount} 个'),
    ];

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('本周概览', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 12),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: items
                .map(
                  (item) => Chip(
                    label: Text('${item.$1} ${item.$2}'),
                    padding: const EdgeInsets.symmetric(horizontal: 10),
                  ),
                )
                .toList(growable: false),
          ),
        ],
      ),
    );
  }
}
