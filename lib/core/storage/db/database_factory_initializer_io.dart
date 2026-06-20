import 'dart:io' show Platform;

import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

bool _databaseFactoryInitialized = false;

void initializeDatabaseFactoryForPlatform() {
  if (_databaseFactoryInitialized || !Platform.isWindows) {
    return;
  }

  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;
  _databaseFactoryInitialized = true;
}
