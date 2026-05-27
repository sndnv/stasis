import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/api/responses/ping.dart';

import '../assertions.dart';

void main() {
  group('Ping interop', () {
    test('decode and re-encode a ping', () {
      assertInterop(
        domain: 'service',
        resource: 'Ping',
        matches: const Ping(id: 'a55bf2c4-c270-4d8b-806b-9f993e8d415a'),
        fromJson: Ping.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
