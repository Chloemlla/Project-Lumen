extension DurationX on Duration {
  String get mmssLabel {
    final totalSeconds = inSeconds;
    final minutes = (totalSeconds ~/ 60).toString().padLeft(2, '0');
    final seconds = (totalSeconds % 60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  String get compactLabel {
    if (inHours > 0) {
      final hours = inHours;
      final minutes = inMinutes.remainder(60);
      return '${hours}h ${minutes}m';
    }

    if (inMinutes > 0) {
      return '${inMinutes}m';
    }

    return '${inSeconds}s';
  }
}
