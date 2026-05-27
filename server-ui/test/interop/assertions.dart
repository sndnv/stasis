import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';

import 'interop_resources.dart';

void assertInterop<T>({
  required String domain,
  required String resource,
  required T matches,
  required T Function(Map<String, Object?> json) fromJson,
  required Map<String, Object?> Function(T value) toJson,
}) {
  final raw = InteropResources.load(domain, resource);
  final parsed = jsonDecode(raw) as Map<String, Object?>;

  expect(fromJson(parsed), matches);

  final encoded = jsonEncode(toJson(matches));
  expect(InteropResources.tree(encoded), InteropResources.tree(raw));
}
