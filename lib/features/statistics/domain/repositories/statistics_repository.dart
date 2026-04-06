import 'package:project_lumen/core/storage/db/daos/daily_eye_stats_dao.dart';
import 'package:project_lumen/core/storage/db/daos/daily_pomodoro_stats_dao.dart';
import 'package:project_lumen/features/statistics/domain/models/daily_eye_stat.dart';
import 'package:project_lumen/features/statistics/domain/models/daily_pomodoro_stat.dart';
import 'package:project_lumen/features/statistics/domain/models/statistics_summary.dart';

abstract class StatisticsRepository {
  Future<StatisticsSummary> loadSummary();

  Future<List<DailyEyeStat>> loadEyeStats({int days = 30});

  Future<List<DailyPomodoroStat>> loadPomodoroStats({int days = 30});
}

class LocalStatisticsRepository implements StatisticsRepository {
  LocalStatisticsRepository({
    required DailyEyeStatsDao dailyEyeStatsDao,
    required DailyPomodoroStatsDao dailyPomodoroStatsDao,
  }) : _dailyEyeStatsDao = dailyEyeStatsDao,
       _dailyPomodoroStatsDao = dailyPomodoroStatsDao;

  final DailyEyeStatsDao _dailyEyeStatsDao;
  final DailyPomodoroStatsDao _dailyPomodoroStatsDao;

  @override
  Future<List<DailyEyeStat>> loadEyeStats({int days = 30}) async {
    final since = DateTime.now().subtract(Duration(days: days - 1));
    final rows = await _dailyEyeStatsDao.fetchSince(_isoDate(since));
    return rows.map(DailyEyeStat.fromMap).toList(growable: false);
  }

  @override
  Future<List<DailyPomodoroStat>> loadPomodoroStats({int days = 30}) async {
    final since = DateTime.now().subtract(Duration(days: days - 1));
    final rows = await _dailyPomodoroStatsDao.fetchSince(_isoDate(since));
    return rows.map(DailyPomodoroStat.fromMap).toList(growable: false);
  }

  @override
  Future<StatisticsSummary> loadSummary() async {
    final weeklyEye = await loadEyeStats(days: 7);
    final monthlyEye = await loadEyeStats(days: 30);
    final weeklyPomodoro = await loadPomodoroStats(days: 7);
    final monthlyPomodoro = await loadPomodoroStats(days: 30);

    return StatisticsSummary(
      weeklyWorkingSeconds: weeklyEye.fold(
        0,
        (sum, stat) => sum + stat.workingSeconds,
      ),
      weeklyRestSeconds: weeklyEye.fold(
        0,
        (sum, stat) => sum + stat.restSeconds,
      ),
      weeklySkipCount: weeklyEye.fold(0, (sum, stat) => sum + stat.skipCount),
      weeklyTomatoCount: weeklyPomodoro.fold(
        0,
        (sum, stat) => sum + stat.completedTomatoCount,
      ),
      monthlyWorkingSeconds: monthlyEye.fold(
        0,
        (sum, stat) => sum + stat.workingSeconds,
      ),
      monthlyRestSeconds: monthlyEye.fold(
        0,
        (sum, stat) => sum + stat.restSeconds,
      ),
      monthlySkipCount: monthlyEye.fold(0, (sum, stat) => sum + stat.skipCount),
      monthlyTomatoCount: monthlyPomodoro.fold(
        0,
        (sum, stat) => sum + stat.completedTomatoCount,
      ),
    );
  }

  String _isoDate(DateTime date) {
    final month = date.month.toString().padLeft(2, '0');
    final day = date.day.toString().padLeft(2, '0');
    return '${date.year}-$month-$day';
  }
}
