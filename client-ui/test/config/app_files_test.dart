import 'package:stasis_client_ui/config/app_files.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('App Files should', () {
    test('support loading all application files', () async {
      const configDir = './test/resources/app_files';

      final files = AppFiles.load(configDir: configDir);

      expect(files.paths.config, '$configDir/client.conf');
      expect(files.paths.rules, '$configDir/client.rules');
      expect(files.paths.schedules, '$configDir/client.schedules');
      expect(files.paths.apiToken, '$configDir/api-token');

      expect(files.config.isEmpty(), false);
      expect(files.rules, ['test-rules']);
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

      expect(files.paths.config, '/tmp');
      expect(files.paths.rules, '/tmp');
      expect(files.paths.schedules, '/tmp');
      expect(files.paths.apiToken, '/tmp');

      expect(files.config.isEmpty(), true);
      expect(files.rules, []);
      expect(files.schedules, []);
      expect(files.apiToken, null);
    });
  });
}
