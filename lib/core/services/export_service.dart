import 'package:project_lumen/features/statistics/domain/models/daily_eye_stat.dart';

class ExportService {
  const ExportService();

  String buildEyeStatsCsv(List<DailyEyeStat> stats) {
    final rows = <String>[
      'date,working_minutes,rest_minutes,skip_count,completed_break_count',
      ...stats.map(
        (stat) => [
          stat.statDate,
          (stat.workingSeconds / 60).round(),
          (stat.restSeconds / 60).round(),
          stat.skipCount,
          stat.completedBreakCount,
        ].join(','),
      ),
    ];

    return rows.join('\n');
  }
}
