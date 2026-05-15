import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:stasis_client_ui/config/app_dirs.dart';

void main() {
  group('An OperatingSystem should', () {
    test('be created from a valid name', () {
      expect(OperatingSystem.from('linux'), OperatingSystem.linux);
      expect(OperatingSystem.from('macos'), OperatingSystem.macos);
      expect(OperatingSystem.from('windows'), OperatingSystem.windows);
    });

    test('be created from a valid name with different casing', () {
      expect(OperatingSystem.from('Linux'), OperatingSystem.linux);
      expect(OperatingSystem.from('MacOS'), OperatingSystem.macos);
      expect(OperatingSystem.from('WINDOWS'), OperatingSystem.windows);
    });

    test('fail to be created from an invalid name', () {
      expect(() => OperatingSystem.from('other'), throwsA(const TypeMatcher<UnsupportedError>()));
    });
  });

  group('AppDirs should', () {
    test('provide the user config directory on Linux', () {
      final result = AppDirs.getUserConfigDir(
        applicationName: 'test-app',
        operatingSystem: 'linux',
        environment: {'HOME': '/home/user'},
      );

      expect(result, '/home/user/.config/test-app');
    });

    test('provide the user config directory on macOS', () {
      final result = AppDirs.getUserConfigDir(
        applicationName: 'test-app',
        operatingSystem: 'macos',
        environment: {'HOME': '/Users/user'},
      );

      expect(result, '/Users/user/Library/Preferences/test-app');
    });

    test('provide the user config directory on Windows with LOCALAPPDATA', () {
      final result = AppDirs.getUserConfigDir(
        applicationName: 'test-app',
        operatingSystem: 'windows',
        environment: {
          'USERPROFILE': 'C:\\Users\\user',
          'LOCALAPPDATA': 'C:\\Users\\user\\AppData\\Local',
        },
      );

      expect(result, 'C:\\Users\\user\\AppData\\Local\\test-app');
    });

    test('provide the user config directory on Windows without LOCALAPPDATA', () {
      final result = AppDirs.getUserConfigDir(
        applicationName: 'test-app',
        operatingSystem: 'windows',
        environment: {'USERPROFILE': 'C:\\Users\\user'},
      );

      expect(result, 'C:\\Users\\user\\AppData\\Local\\test-app');
    });

    test('provide the user config directory with XDG_CONFIG_HOME override', () {
      final result = AppDirs.getUserConfigDir(
        applicationName: 'test-app',
        operatingSystem: 'linux',
        environment: {
          'HOME': '/home/user',
          'XDG_CONFIG_HOME': '/custom/config',
        },
      );

      expect(result, '/custom/config${Platform.pathSeparator}test-app');
    });

    test('trim trailing slashes from environment paths', () {
      final result = AppDirs.getUserConfigDir(
        applicationName: 'test-app',
        operatingSystem: 'linux',
        environment: {'HOME': '/home/user/'},
      );

      expect(result, '/home/user/.config/test-app');
    });

    test('use default home when HOME is not set', () {
      final result = AppDirs.getUserConfigDir(
        applicationName: 'test-app',
        operatingSystem: 'linux',
        environment: {},
      );

      expect(result, '~/.config/test-app');
    });

    test('use default home when USERPROFILE is not set on Windows', () {
      final result = AppDirs.getUserConfigDir(
        applicationName: 'test-app',
        operatingSystem: 'windows',
        environment: {},
      );

      expect(result, '~\\AppData\\Local\\test-app');
    });
  });

  group('A String should', () {
    test('have trailing slashes trimmed', () {
      expect('test/'.trimTrailingSlash(), 'test');
      expect('test'.trimTrailingSlash(), 'test');
      expect('/'.trimTrailingSlash(), '');
      expect(' test/ '.trimTrailingSlash(), 'test');
    });
  });
}
