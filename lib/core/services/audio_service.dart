import 'package:flutter/services.dart';

abstract class AppAudioService {
  Future<void> playPreAlert();

  Future<void> playRestEnded();

  Future<void> playPomodoroWorkStart();

  Future<void> playPomodoroWorkEnd();
}

class SystemAudioService implements AppAudioService {
  @override
  Future<void> playPomodoroWorkEnd() {
    return SystemSound.play(SystemSoundType.alert);
  }

  @override
  Future<void> playPomodoroWorkStart() {
    return SystemSound.play(SystemSoundType.click);
  }

  @override
  Future<void> playPreAlert() {
    return SystemSound.play(SystemSoundType.click);
  }

  @override
  Future<void> playRestEnded() {
    return SystemSound.play(SystemSoundType.alert);
  }
}

class SilentAudioService implements AppAudioService {
  @override
  Future<void> playPomodoroWorkEnd() async {}

  @override
  Future<void> playPomodoroWorkStart() async {}

  @override
  Future<void> playPreAlert() async {}

  @override
  Future<void> playRestEnded() async {}
}
