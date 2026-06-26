import 'dart:io' show Platform;

import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart'
    deferred as sqflite_ffi
    hide SqfliteFfiMethodCallHandler;

bool _databaseFactoryInitialized = false;

Future<void> initializeDatabaseFactoryForPlatform() async {
  if (_databaseFactoryInitialized || !Platform.isWindows) {
    return;
  }

  await sqflite_ffi.loadLibrary();
  sqflite_ffi.sqfliteFfiInit();
  databaseFactory = sqflite_ffi.databaseFactoryFfi;
  _databaseFactoryInitialized = true;
}
