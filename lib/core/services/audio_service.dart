abstract class AppAudioService {
  Future<void> playRestEnded();

  Future<void> playPomodoroWorkStart();

  Future<void> playPomodoroWorkEnd();
}

class SilentAudioService implements AppAudioService {
  @override
  Future<void> playPomodoroWorkEnd() async {}

  @override
  Future<void> playPomodoroWorkStart() async {}

  @override
  Future<void> playRestEnded() async {}
}
