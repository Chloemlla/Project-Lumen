enum ActiveEngine { idle, reminder, pomodoro }

extension ActiveEngineX on ActiveEngine {
  String get storageValue => switch (this) {
    ActiveEngine.idle => 'idle',
    ActiveEngine.reminder => 'reminder',
    ActiveEngine.pomodoro => 'pomodoro',
  };
}

ActiveEngine activeEngineFromStorage(String? value) {
  return ActiveEngine.values.firstWhere(
    (engine) => engine.storageValue == value,
    orElse: () => ActiveEngine.idle,
  );
}
