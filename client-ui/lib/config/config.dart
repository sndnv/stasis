import 'dart:convert';
import 'dart:io';

/// A config map from dot-separated (a.b.c) paths/keys to values (bool, int, double, string, etc).
/// Notes:
///   - meant to partially mimic the Config interface of https://github.com/lightbend/config
///   - a minimum number of features are supported, only for the purposes of this codebase
///   - durations, memory sizes and lists are NOT supported
class Config {
  Config({required config}) {
    _raw = config;
  }

  late Map<String, dynamic> _raw;

  Map<String, dynamic> get raw => _raw;

  Config sanitized() {
    final sanitized = _raw.map((k, v) {
      final key = k.toLowerCase();
      if (v is Map<String, dynamic>) {
        return MapEntry(k, Config(config: v).sanitized().raw);
      } else if (key.contains('password') || key.contains('secret')) {
        return MapEntry(k, '<removed>');
      } else {
        return MapEntry(k, v ?? '<missing>');
      }
    });

    return Config(config: sanitized);
  }

  @override
  String toString() => _raw.toString();

  /// Checks whether a value is present and non-null at the given path
  bool hasPath(String path) {
    return _valueAt(path) != null;
  }

  /// Returns true if the configuration is empty
  bool isEmpty() {
    return _raw.isEmpty;
  }

  /// Retrieves the boolean value at the requested path.
  bool getBoolean(String path, {bool? withDefault}) => _getT<bool>(path, withDefault);

  /// Retrieves the integer value at the requested path.
  int getInt(String path, {int? withDefault}) => _getT<int>(path, withDefault);

  /// Retrieves the floating-point value at the requested path.
  double getDouble(String path, {double? withDefault}) => _getT<double>(path, withDefault);

  /// Retrieves the string value at the requested path.
  String getString(String path, {String? withDefault}) => _getT<String>(path, withDefault);

  /// Retrieves the nested config value at the requested path.
  Config getConfig(String path) {
    final value = _valueAt(path);

    if (value is Map<String, dynamic>) {
      return Config(config: value);
    } else if (value == null) {
      throw ConfigMissingException(path);
    } else {
      throw WrongTypeException(path, 'Config', value.runtimeType.toString());
    }
  }

  dynamic _valueAt(String path) {
    final actual = path.split('.');

    return actual.fold<dynamic>(_raw, (config, key) {
      if (config is Map<String, dynamic>) {
        return config[key];
      } else {
        return null;
      }
    });
  }

  T _getT<T>(String path, T? withDefault) {
    final value = _valueAt(path);

    if (value is T) {
      return value;
    } else if (value == null) {
      if (withDefault != null) {
        return withDefault;
      } else {
        throw ConfigMissingException(path);
      }
    } else {
      throw WrongTypeException(path, T.toString(), value.runtimeType.toString());
    }
  }
}

/// Factory for creating `Config` instances.
class ConfigFactory {
  static Config empty() {
    const Map<String, dynamic> map = {};
    return Config(config: map);
  }

  static Config load({required String path}) {
    return _loadFromJson(path: path) ?? _loadFromHocon(path: path);
  }

  /// Loads config from the specified (HOCON) file path.
  /// Notes:
  ///   - the configuration parsing is very basic and does not fully cover all features of HOCON
  ///   - no real validation is done upfront and invalid config might be loaded,
  ///     only to fail when config values are requested
  static Config _loadFromHocon({required String path}) {
    final objectKeyMatcher = RegExp(r'^\s*([\w\-_.]+)\s*[:=]?\s*{');
    final valueKeyMatcher = RegExp(r'^\s*([\w\-_.]+)\s*[:=]\s*(["$\w\[].*)');
    final envVarMatcher = RegExp(r'\${\?(.*)}');

    // will fail if # or // is inside a value AND there's an inline comment
    final inlineCommentMatcher = RegExp(r'(//|#).+[^"]$');

    try {
      final content = File(path).readAsLinesSync().expand<String>((raw) {
        // discards comments

        final line = raw.trim();
        if (line.isEmpty || line.startsWith('#') || line.startsWith('//')) {
          return []; // skips empty or comment lines
        } else {
          return [line.replaceAll(inlineCommentMatcher, '').trim()]; // removes inline comments
        }
      }).expand<String>((line) {
        // expands env vars (references to other values are not supported)

        final envVar = envVarMatcher.firstMatch(line)?.group(1);
        if (envVar != null) {
          final value = Platform.environment[envVar];
          if (value != null) {
            return [line.replaceAll(envVarMatcher, value)]; // env var was provided, overriding
          } else {
            return []; // no env var was provided so the override is not needed
          }
        } else {
          return [line]; // no env var was requested, keeping original line
        }
      }).map<String>((line) {
        // converts keys and values to JSON

        final objectKey = objectKeyMatcher.firstMatch(line)?.group(1);
        if (objectKey != null) {
          return '"$objectKey":{';
        } else {
          final match = valueKeyMatcher.firstMatch(line);
          if (match != null) {
            final key = match.group(1)?.replaceAll('"', '') ?? '';
            final value = match.group(2) ?? '';

            final processedValue =
                _asNumber(value) ?? _asBool(value) ?? _asNull(value) ?? _asList(value) ?? _asString(value);

            return '"$key":$processedValue,';
          } else {
            return '$line,';
          }
        }
      });

      final json = '{${(content.join())}}'.replaceAll(',}', '}');

      return Config(config: jsonDecode(json));
    } on FileSystemException {
      throw ConfigFileNotAvailableException(path);
    } on FormatException catch (e) {
      throw InvalidConfigFileException(path, e.toString());
    }
  }

  static String? _asNumber(String value) {
    return double.tryParse(value) != null ? value : null;
  }

  static String? _asBool(String value) {
    switch (value.toLowerCase()) {
      case 'yes':
      case 'on':
      case 'true':
        return 'true';

      case 'no':
      case 'off':
      case 'false':
        return 'false';

      default:
        return null;
    }
  }

  static String? _asNull(String value) {
    return value.toLowerCase() == 'null' ? 'null' : null;
  }

  static String? _asList(String value) {
    if (value.startsWith('[') && value.endsWith(']')) {
      return '[]';
    } else {
      return null;
    }
  }

  static String _asString(String value) {
    return '"${value.replaceAll('"', '')}"';
  }

  static Config? _loadFromJson({required String path}) {
    try {
      return Config(config: jsonDecode(File(path).readAsStringSync()));
    } on FileSystemException {
      return null;
    } on FormatException {
      return null;
    }
  }
}

class ConfigFileNotAvailableException implements Exception {
  ConfigFileNotAvailableException(this.path);

  String path;
}

class InvalidConfigFileException implements Exception {
  InvalidConfigFileException(this.path, this.message);

  String path;
  String message;
}

class ConfigMissingException implements Exception {
  ConfigMissingException(this.path);

  String path;
}

class WrongTypeException implements Exception {
  WrongTypeException(this.path, this.expected, this.actual);

  String path;
  String expected;
  String actual;
}
