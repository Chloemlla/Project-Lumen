class DailyEyeStat {
  const DailyEyeStat({
    required this.statDate,
    required this.workingSeconds,
    required this.restSeconds,
    required this.skipCount,
    required this.completedBreakCount,
    required this.preAlertCount,
    required this.updatedAt,
  });

  final String statDate;
  final int workingSeconds;
  final int restSeconds;
  final int skipCount;
  final int completedBreakCount;
  final int preAlertCount;
  final DateTime updatedAt;

  factory DailyEyeStat.fromMap(Map<String, Object?> map) {
    return DailyEyeStat(
      statDate: (map['stat_date'] as String?) ?? '',
      workingSeconds: (map['working_seconds'] as int?) ?? 0,
      restSeconds: (map['rest_seconds'] as int?) ?? 0,
      skipCount: (map['skip_count'] as int?) ?? 0,
      completedBreakCount: (map['completed_break_count'] as int?) ?? 0,
      preAlertCount: (map['pre_alert_count'] as int?) ?? 0,
      updatedAt:
          DateTime.tryParse((map['updated_at'] as String?) ?? '') ??
          DateTime.fromMillisecondsSinceEpoch(0),
    );
  }
}
