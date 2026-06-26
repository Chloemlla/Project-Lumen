import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

class AppLogSink {
  Future<File>? _fileFuture;
  String? _logFilePath;

  String? get logFilePath => _logFilePath;

  Future<void> writeLine(String line) async {
    try {
      final file = await (_fileFuture ??= _openFile());
      await file.writeAsString('$line\n', mode: FileMode.append, flush: true);
    } catch (_) {
      // Logging must never become a startup failure.
    }
  }

  Future<File> _openFile() async {
    final supportDirectory = await getApplicationSupportDirectory();
    final logDirectory = Directory(p.join(supportDirectory.path, 'logs'));
    if (!await logDirectory.exists()) {
      await logDirectory.create(recursive: true);
    }

    final file = File(p.join(logDirectory.path, 'project_lumen.log'));
    _logFilePath = file.path;
    return file;
  }
}
