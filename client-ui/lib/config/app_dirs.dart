import 'dart:io';

class AppDirs {
  static String getUserConfigDir({
    required String applicationName,
    String? operatingSystem,
    Map<String, String>? environment,
  }) {
    final os = OperatingSystem.from(operatingSystem ?? Platform.operatingSystem);
    final env = environment ?? Platform.environment;
    final separator = Platform.pathSeparator;
    final xdgConfigHome = env['XDG_CONFIG_HOME']?.trimTrailingSlash();

    if (xdgConfigHome != null) {
      return '$xdgConfigHome$separator$applicationName';
    } else {
      final userHome = env[os == OperatingSystem.windows ? 'USERPROFILE' : 'HOME']?.trimTrailingSlash() ?? '~';
      switch (os) {
        case OperatingSystem.linux:
          return '$userHome/.config/$applicationName';
        case OperatingSystem.macos:
          return '$userHome/Library/Preferences/$applicationName';
        case OperatingSystem.windows:
          final localAppData = env['LOCALAPPDATA']?.trimTrailingSlash() ?? '$userHome\\AppData\\Local';
          return '$localAppData\\$applicationName';
      }
    }
  }
}

enum OperatingSystem {
  linux,
  macos,
  windows;

  factory OperatingSystem.from(String name) {
    return OperatingSystem.values.firstWhere(
      (os) => os.name == name.toLowerCase(),
      orElse: () => throw UnsupportedError('Unsupported operating system: [$name]'),
    );
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
