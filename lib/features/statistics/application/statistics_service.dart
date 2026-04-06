import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:project_lumen/app/bootstrap.dart';
import 'package:project_lumen/features/statistics/domain/models/daily_eye_stat.dart';
import 'package:project_lumen/features/statistics/domain/models/daily_pomodoro_stat.dart';
import 'package:project_lumen/features/statistics/domain/models/statistics_summary.dart';
import 'package:project_lumen/features/statistics/domain/repositories/statistics_repository.dart';

final statisticsServiceProvider = Provider<StatisticsService>((ref) {
  return StatisticsService(ref.watch(statisticsRepositoryProvider));
});

final statisticsSummaryProvider = FutureProvider<StatisticsSummary>((ref) {
  return ref.watch(statisticsServiceProvider).loadSummary();
});

final monthlyEyeStatsProvider = FutureProvider<List<DailyEyeStat>>((ref) {
  return ref.watch(statisticsServiceProvider).loadEyeStats(days: 30);
});

final monthlyPomodoroStatsProvider = FutureProvider<List<DailyPomodoroStat>>((
  ref,
) {
  return ref.watch(statisticsServiceProvider).loadPomodoroStats(days: 30);
});

class StatisticsService {
  const StatisticsService(this._repository);

  final StatisticsRepository _repository;

  Future<StatisticsSummary> loadSummary() => _repository.loadSummary();

  Future<List<DailyEyeStat>> loadEyeStats({int days = 30}) {
    return _repository.loadEyeStats(days: days);
  }

  Future<List<DailyPomodoroStat>> loadPomodoroStats({int days = 30}) {
    return _repository.loadPomodoroStats(days: days);
  }
}
