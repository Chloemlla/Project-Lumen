import 'package:project_lumen/core/storage/db/daos/app_settings_dao.dart';
import 'package:project_lumen/features/settings/domain/models/app_settings.dart';

abstract class SettingsRepository {
  Future<void> ensureInitialized();

  Future<AppSettings> getSettings();

  Future<void> saveSettings(AppSettings settings);
}

class LocalSettingsRepository implements SettingsRepository {
  LocalSettingsRepository(this._appSettingsDao);

  final AppSettingsDao _appSettingsDao;

  @override
  Future<void> ensureInitialized() async {
    final current = await _appSettingsDao.fetch();
    if (current != null) {
      return;
    }

    await _appSettingsDao.upsert(AppSettings.defaults().toMap());
  }

  @override
  Future<AppSettings> getSettings() async {
    final row = await _appSettingsDao.fetch();
    if (row == null) {
      final defaults = AppSettings.defaults();
      await saveSettings(defaults);
      return defaults;
    }

    return AppSettings.fromMap(row);
  }

  @override
  Future<void> saveSettings(AppSettings settings) {
    return _appSettingsDao.upsert(settings.toMap());
  }
}
