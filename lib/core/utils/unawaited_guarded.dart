import 'dart:async';

import 'package:project_lumen/core/logging/app_logger.dart';

void unawaitedGuarded(Future<void> future, {String operation = 'async task'}) {
  unawaited(
    future.onError<Object>((error, stackTrace) {
      appLogger.error('$operation failed', error, stackTrace);
      Zone.current.handleUncaughtError(error, stackTrace);
    }),
  );
}
