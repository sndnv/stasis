import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/devices/device_key.dart';

import '../assertions.dart';

void main() {
  group('DeviceKey interop', () {
    test('decode and re-encode a device key', () {
      assertInterop(
        domain: 'devices',
        resource: 'DeviceKey',
        matches: DeviceKey(
          owner: '1528e306-7218-44c2-857c-96c3d64e29b6',
          device: '344f2a22-f52e-4df0-88b8-ecb42ed54478',
          created: DateTime.parse('2026-05-01T09:00:00Z'),
        ),
        fromJson: DeviceKey.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
