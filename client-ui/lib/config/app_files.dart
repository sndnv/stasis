import 'dart:io';

import 'package:stasis_client_ui/config/api_token.dart';
import 'package:stasis_client_ui/config/config.dart';

class AppFiles {
  static const String configFileName = 'client.conf';
  static const String apiTokenFileName = 'api-token';

  static const String defaultSchedulesFileName = 'client.schedules';

  AppFiles({
    required this.config,
    required this.schedules,
    required this.apiToken,
    required this.paths,
  });

  final Config config;
  final List<String> schedules;
  final String? apiToken;
  final AppFilesPaths paths;

  static AppFiles empty() {
    return AppFiles(
      config: ConfigFactory.empty(),
      schedules: [],
      apiToken: null,
      paths: AppFilesPaths(
        config: '/tmp',
        schedules: '/tmp',
        apiToken: '/tmp',
      ),
    );
  }

  static AppFiles load({required String configDir}) {
    final configFilePath = '$configDir/$configFileName';
    final config = ConfigFactory.load(path: configFilePath);

    final schedulesFileName = config.getString(
      'stasis.client.ops.scheduling.schedules-file',
      withDefault: defaultSchedulesFileName,
    );

    final paths = AppFilesPaths(
      config: configFilePath,
      schedules: '$configDir/$schedulesFileName',
      apiToken: '$configDir/$apiTokenFileName',
    );

    return AppFiles(
      config: config,
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
    required this.schedules,
    required this.apiToken,
  });

  final String config;
  final String schedules;
  final String apiToken;
}

class FileNotAvailableException implements Exception {
  FileNotAvailableException(this.path);

  String path;
}
