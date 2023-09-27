import 'dart:io';

class AppDirs {
  static String getUserConfigDir({required String applicationName}) {
    final xdgConfigHome = Platform.environment['XDG_CONFIG_HOME']?.trimTrailingSlash();

    if (xdgConfigHome != null) {
      return '$xdgConfigHome/$applicationName';
    } else {
      final userHome = Platform.environment['HOME']?.trimTrailingSlash() ?? '~';
      if (Platform.isLinux) {
        return '$userHome/.config/$applicationName';
      } else if (Platform.isMacOS) {
        return '$userHome/Library/Preferences/$applicationName';
      } else {
        throw UnsupportedError('Unsupported operating system: [${Platform.operatingSystem}]');
      }
    }
  }
}

extension UriString on String {
  String trimTrailingSlash() {
    final string = trim();
    if (string.endsWith('/')) {
      return string.substring(0, string.length - 1);
    } else {
      return string;
    }
  }
}
