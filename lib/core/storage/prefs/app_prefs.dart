import 'package:shared_preferences/shared_preferences.dart';

class AppPrefs {
  AppPrefs(this._sharedPreferences);

  final SharedPreferences _sharedPreferences;

  static const _lastRouteKey = 'last_route';
  static const _hasSeededTemplatesKey = 'has_seeded_templates';

  String? getLastRoute() => _sharedPreferences.getString(_lastRouteKey);

  Future<void> setLastRoute(String route) async {
    await _sharedPreferences.setString(_lastRouteKey, route);
  }

  bool get hasSeededTemplates =>
      _sharedPreferences.getBool(_hasSeededTemplatesKey) ?? false;

  Future<void> setHasSeededTemplates(bool value) async {
    await _sharedPreferences.setBool(_hasSeededTemplatesKey, value);
  }
}
