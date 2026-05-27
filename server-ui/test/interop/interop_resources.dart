import 'dart:convert';
import 'dart:io';

class InteropResources {
  static String load(String domain, String resource) {
    final file = File('../shared/src/test/resources/interop/$domain/$resource.json');
    if (!file.existsSync()) {
      throw StateError('Interop resource not found: [${file.path}]');
    }
    return file.readAsStringSync();
  }

  static Object? tree(String raw) => _normalize(jsonDecode(raw));

  static Object? _normalize(Object? value) {
    if (value is Map) {
      final out = <String, Object?>{};
      for (final entry in value.entries) {
        final key = entry.key as String;
        if (_ignoredKeys.contains(key) || entry.value == null) continue;
        out[key] = _normalize(entry.value);
      }
      return out;
    }
    if (value is List) {
      return value.map(_normalize).toList();
    }
    if (value is String) {
      if (_uuidPattern.hasMatch(value)) return value.toLowerCase();
      if (_dateTimePattern.hasMatch(value)) {
        final parsed = DateTime.tryParse(value);
        if (parsed != null) return parsed.toUtc().toIso8601String();
      }
      return value;
    }
    return value;
  }

  static const Set<String> _ignoredKeys = {
    'next_invocation',
    'use_query_string',
    'context',
    'additional_config',
  };

  static final RegExp _uuidPattern = RegExp(
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
  );

  static final RegExp _dateTimePattern = RegExp(
    r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:?\d{2})?$',
  );
}
