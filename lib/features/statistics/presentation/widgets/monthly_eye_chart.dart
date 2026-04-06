import 'package:flutter/material.dart';
import 'package:project_lumen/features/statistics/domain/models/daily_eye_stat.dart';
import 'package:project_lumen/shared/widgets/app_card.dart';

class MonthlyEyeChart extends StatelessWidget {
  const MonthlyEyeChart({super.key, required this.stats});

  final List<DailyEyeStat> stats;

  @override
  Widget build(BuildContext context) {
    final maxValue = stats.fold<int>(
      1,
      (max, item) => item.workingSeconds > max ? item.workingSeconds : max,
    );

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('月度用眼趋势', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 16),
          SizedBox(
            height: 180,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: stats
                  .take(14)
                  .map(
                    (item) => Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 3),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.end,
                          children: [
                            Container(
                              height: 120 * (item.workingSeconds / maxValue),
                              decoration: BoxDecoration(
                                color: Theme.of(context).colorScheme.primary,
                                borderRadius: BorderRadius.circular(99),
                              ),
                            ),
                            const SizedBox(height: 8),
                            Text(
                              item.statDate.substring(5),
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                          ],
                        ),
                      ),
                    ),
                  )
                  .toList(growable: false),
            ),
          ),
        ],
      ),
    );
  }
}
