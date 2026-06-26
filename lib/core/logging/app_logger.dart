import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:project_lumen/core/logging/app_log_sink.dart';

final appLogger = AppLogger._();

class AppLogger {
  AppLogger._();

  final AppLogSink _sink = AppLogSink();
  final List<String> _recentLines = <String>[];
  int _sequence = 0;

  List<String> get recentLines => List.unmodifiable(_recentLines);

  String? get logFilePath => _sink.logFilePath;

  void info(String message, [Map<String, Object?> context = const {}]) {
    _write('INFO', message, context: context);
  }

  void warning(String message, [Map<String, Object?> context = const {}]) {
    _write('WARN', message, context: context);
  }

  void error(
    String message,
    Object error, [
    StackTrace? stackTrace,
    Map<String, Object?> context = const {},
  ]) {
    _write(
      'ERROR',
      message,
      error: error,
      stackTrace: stackTrace,
      context: context,
    );
  }

  void flutterError(FlutterErrorDetails details) {
    error(
      details.context?.toDescription() ?? 'Flutter framework error',
      details.exception,
      details.stack,
      {'library': details.library, 'silent': details.silent},
    );
  }

  Future<T> trace<T>(String operation, Future<T> Function() action) async {
    final stopwatch = Stopwatch()..start();
    info('$operation started');

    try {
      final result = await action();
      info('$operation completed', {
        'elapsedMs': stopwatch.elapsedMilliseconds,
      });
      return result;
    } catch (error, stackTrace) {
      this.error('$operation failed', error, stackTrace, {
        'elapsedMs': stopwatch.elapsedMilliseconds,
      });
      rethrow;
    }
  }

  void _write(
    String level,
    String message, {
    Object? error,
    StackTrace? stackTrace,
    Map<String, Object?> context = const {},
  }) {
    final timestamp = DateTime.now().toIso8601String();
    final id = (++_sequence).toString().padLeft(4, '0');
    final contextText = context.entries
        .where((entry) => entry.value != null)
        .map((entry) => '${entry.key}=${entry.value}')
        .join(' ');
    final errorText = error == null ? '' : ' error=$error';
    final line = [
      '[$timestamp]',
      '[$id]',
      '[$level]',
      message,
      if (contextText.isNotEmpty) contextText,
      if (errorText.isNotEmpty) errorText,
    ].join(' ');

    _remember(line);
    debugPrint(line);
    unawaited(_sink.writeLine(line));

    if (stackTrace != null) {
      for (final stackLine in stackTrace.toString().trimRight().split('\n')) {
        final formatted = '[$timestamp] [$id] [STACK] $stackLine';
        _remember(formatted);
        debugPrint(formatted);
        unawaited(_sink.writeLine(formatted));
      }
    }
  }

  void _remember(String line) {
    _recentLines.add(line);
    if (_recentLines.length > 200) {
      _recentLines.removeRange(0, _recentLines.length - 200);
    }
  }
}
