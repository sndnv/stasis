import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:stasis_client_ui/config/app_files.dart';

void main() {
  group('App Files should', () {
    test('support loading all application files', () async {
      const configDir = './test/resources/app_files';

      final files = AppFiles.load(configDir: configDir);

      expect(files.paths.config, '$configDir${Platform.pathSeparator}client.conf');
      expect(files.paths.schedules, '$configDir${Platform.pathSeparator}client.schedules');
      expect(files.paths.apiToken, '$configDir${Platform.pathSeparator}api-token');

      expect(files.config.isEmpty(), false);
      expect(files.schedules, ['test-schedules']);
      expect(files.apiToken, 'test-token');
    });

    test('fail to load missing application files', () async {
      const configDir = './test/resources/app_files/invalid';

      expect(
        () => AppFiles.load(configDir: configDir),
        throwsA(const TypeMatcher<FileNotAvailableException>()),
      );
    });

    test('provide empty app files', () async {
      final files = AppFiles.empty();

      expect(files.paths.config, Directory.systemTemp.path);
      expect(files.paths.schedules, Directory.systemTemp.path);
      expect(files.paths.apiToken, Directory.systemTemp.path);

      expect(files.config.isEmpty(), true);
      expect(files.schedules, []);
      expect(files.apiToken, null);
    });
  });
}
