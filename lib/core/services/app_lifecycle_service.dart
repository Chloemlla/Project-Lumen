import 'package:flutter/widgets.dart';

class AppLifecycleService with WidgetsBindingObserver {
  AppLifecycleState currentState = AppLifecycleState.resumed;

  void register() {
    WidgetsBinding.instance.addObserver(this);
  }

  void unregister() {
    WidgetsBinding.instance.removeObserver(this);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    currentState = state;
  }
}
