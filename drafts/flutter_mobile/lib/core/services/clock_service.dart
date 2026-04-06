abstract class ClockService {
  DateTime now();
}

class SystemClockService implements ClockService {
  @override
  DateTime now() => DateTime.now();
}

