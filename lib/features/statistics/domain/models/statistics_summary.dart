class StatisticsSummary {
  const StatisticsSummary({
    required this.weeklyWorkingSeconds,
    required this.weeklyRestSeconds,
    required this.weeklySkipCount,
    required this.weeklyTomatoCount,
    required this.monthlyWorkingSeconds,
    required this.monthlyRestSeconds,
    required this.monthlySkipCount,
    required this.monthlyTomatoCount,
  });

  final int weeklyWorkingSeconds;
  final int weeklyRestSeconds;
  final int weeklySkipCount;
  final int weeklyTomatoCount;
  final int monthlyWorkingSeconds;
  final int monthlyRestSeconds;
  final int monthlySkipCount;
  final int monthlyTomatoCount;

  const StatisticsSummary.empty()
    : weeklyWorkingSeconds = 0,
      weeklyRestSeconds = 0,
      weeklySkipCount = 0,
      weeklyTomatoCount = 0,
      monthlyWorkingSeconds = 0,
      monthlyRestSeconds = 0,
      monthlySkipCount = 0,
      monthlyTomatoCount = 0;
}
