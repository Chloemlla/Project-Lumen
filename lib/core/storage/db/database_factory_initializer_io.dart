import 'dart:io' show Platform;

import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart'
    as sqflite_ffi
    show databaseFactoryFfi, sqfliteFfiInit;

import 'package:project_lumen/core/logging/app_logger.dart';

bool _databaseFactoryInitialized = false;

Future<void> initializeDatabaseFactoryForPlatform() async {
  if (_databaseFactoryInitialized || !Platform.isWindows) {
    appLogger.info('Database factory initialization skipped', {
      'initialized': _databaseFactoryInitialized,
      'isWindows': Platform.isWindows,
    });
    return;
  }

  appLogger.info('Initializing Windows sqflite FFI database factory');
  sqflite_ffi.sqfliteFfiInit();
  databaseFactory = sqflite_ffi.databaseFactoryFfi;
  _databaseFactoryInitialized = true;
  appLogger.info('Windows sqflite FFI database factory initialized');
}
