import 'dart:io';

int getConfiguredTimeout({int defaultTimeout = 30}) {
  return int.tryParse(Platform.environment['STASIS_CLIENT_UI_TIMEOUT'] ?? '') ?? defaultTimeout;
}
