import 'dart:io' show Platform;

import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart'
    as sqflite_ffi
    show databaseFactoryFfi, sqfliteFfiInit;

bool _databaseFactoryInitialized = false;

Future<void> initializeDatabaseFactoryForPlatform() async {
  if (_databaseFactoryInitialized || !Platform.isWindows) {
    return;
  }

  sqflite_ffi.sqfliteFfiInit();
  databaseFactory = sqflite_ffi.databaseFactoryFfi;
  _databaseFactoryInitialized = true;
}
