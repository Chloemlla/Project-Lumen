import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:project_lumen/core/services/export_service.dart';
import 'package:project_lumen/features/statistics/application/statistics_service.dart';
import 'package:project_lumen/features/statistics/domain/models/statistics_summary.dart';
import 'package:project_lumen/features/statistics/presentation/widgets/monthly_eye_chart.dart';
import 'package:project_lumen/features/statistics/presentation/widgets/monthly_pomodoro_chart.dart';
import 'package:project_lumen/features/statistics/presentation/widgets/weekly_summary_cards.dart';
import 'package:project_lumen/shared/widgets/app_loading_view.dart';
import 'package:project_lumen/shared/widgets/app_scaffold.dart';

class StatisticsPage extends ConsumerWidget {
  const StatisticsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final summary = ref.watch(statisticsSummaryProvider);
    final eyeStats = ref.watch(monthlyEyeStatsProvider);
    final pomodoroStats = ref.watch(monthlyPomodoroStatsProvider);

    return AppScaffold(
      title: '统计',
      location: '/statistics',
      actions: [
        IconButton(
          onPressed: eyeStats.value == null
              ? null
              : () {
                  final csv = const ExportService().buildEyeStatsCsv(
                    eyeStats.value!,
                  );
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('CSV 已生成，共 ${csv.length} 个字符')),
                  );
                },
          icon: const Icon(Icons.download_outlined),
        ),
      ],
      body: summary.when(
        data: (summaryData) {
          final eyeData = eyeStats.value ?? const [];
          final pomodoroData = pomodoroStats.value ?? const [];
          return _StatisticsContent(
            summary: summaryData,
            eyeStatsLength: eyeData.length,
            child: ListView(
              children: [
                WeeklySummaryCards(summary: summaryData),
                const SizedBox(height: 16),
                MonthlyEyeChart(stats: eyeData),
                const SizedBox(height: 16),
                MonthlyPomodoroChart(stats: pomodoroData),
              ],
            ),
          );
        },
        error: (_, _) => const _StatisticsContent(
          summary: StatisticsSummary.empty(),
          eyeStatsLength: 0,
          child: SizedBox.shrink(),
        ),
        loading: () => const AppLoadingView(),
      ),
    );
  }
}

class _StatisticsContent extends StatelessWidget {
  const _StatisticsContent({
    required this.summary,
    required this.eyeStatsLength,
    required this.child,
  });

  final StatisticsSummary summary;
  final int eyeStatsLength;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    if (eyeStatsLength == 0 &&
        summary.weeklyWorkingSeconds == 0 &&
        summary.monthlyWorkingSeconds == 0 &&
        summary.weeklyTomatoCount == 0 &&
        summary.monthlyTomatoCount == 0) {
      return const Center(child: Text('暂无统计数据'));
    }
    return child;
  }
}
