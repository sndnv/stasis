import 'dart:io';

import 'package:stasis_client_ui/config/api_token.dart';
import 'package:stasis_client_ui/config/config.dart';

class AppFiles {
  static const String configFileName = 'client.conf';
  static const String apiTokenFileName = 'api-token';

  static const String defaultRulesFileName = 'client.rules';
  static const String defaultSchedulesFileName = 'client.schedules';

  AppFiles({
    required this.config,
    required this.rules,
    required this.schedules,
    required this.apiToken,
    required this.paths,
  });

  final Config config;
  final List<String> rules;
  final List<String> schedules;
  final String? apiToken;
  final AppFilesPaths paths;

  static AppFiles load({required String configDir}) {
    final configFilePath = '$configDir/$configFileName';
    final config = ConfigFactory.load(path: configFilePath);

    final rulesFileName = config.getString(
      'stasis.client.ops.backup.rules-file',
      withDefault: defaultRulesFileName,
    );

    final schedulesFileName = config.getString(
      'stasis.client.ops.scheduling.schedules-file',
      withDefault: defaultSchedulesFileName,
    );

    final paths = AppFilesPaths(
      config: configFilePath,
      rules: '$configDir/$rulesFileName',
      schedules: '$configDir/$schedulesFileName',
      apiToken: '$configDir/$apiTokenFileName',
    );

    return AppFiles(
      config: config,
      rules: _loadFile(path: paths.rules),
      schedules: _loadFile(path: paths.schedules),
      apiToken: ApiTokenFactory.load(path: paths.apiToken),
      paths: paths,
    );
  }

  static List<String> _loadFile({required String path}) {
    try {
      return File(path).readAsLinesSync();
    } on FileSystemException {
      throw FileNotAvailableException(path);
    }
  }
}

class AppFilesPaths {
  AppFilesPaths({
    required this.config,
    required this.rules,
    required this.schedules,
    required this.apiToken,
  });

  final String config;
  final String rules;
  final String schedules;
  final String apiToken;
}

class FileNotAvailableException implements Exception {
  FileNotAvailableException(this.path);

  String path;
}
