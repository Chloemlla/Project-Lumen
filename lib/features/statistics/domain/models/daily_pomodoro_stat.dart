class DailyPomodoroStat {
  const DailyPomodoroStat({
    required this.statDate,
    required this.completedTomatoCount,
    required this.restartCount,
    required this.completedFocusSessions,
    required this.totalFocusSeconds,
    required this.totalBreakSeconds,
    required this.updatedAt,
  });

  final String statDate;
  final int completedTomatoCount;
  final int restartCount;
  final int completedFocusSessions;
  final int totalFocusSeconds;
  final int totalBreakSeconds;
  final DateTime updatedAt;

  factory DailyPomodoroStat.fromMap(Map<String, Object?> map) {
    return DailyPomodoroStat(
      statDate: (map['stat_date'] as String?) ?? '',
      completedTomatoCount: (map['completed_tomato_count'] as int?) ?? 0,
      restartCount: (map['restart_count'] as int?) ?? 0,
      completedFocusSessions: (map['completed_focus_sessions'] as int?) ?? 0,
      totalFocusSeconds: (map['total_focus_seconds'] as int?) ?? 0,
      totalBreakSeconds: (map['total_break_seconds'] as int?) ?? 0,
      updatedAt:
          DateTime.tryParse((map['updated_at'] as String?) ?? '') ??
          DateTime.fromMillisecondsSinceEpoch(0),
    );
  }
}
