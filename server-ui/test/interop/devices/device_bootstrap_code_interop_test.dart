import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/api/requests/create_device_own.dart';
import 'package:server_ui/model/devices/device_bootstrap_code.dart';

import '../assertions.dart';

void main() {
  group('DeviceBootstrapCode interop', () {
    test('decode and re-encode an existing-device bootstrap code', () {
      assertInterop(
        domain: 'devices',
        resource: 'DeviceBootstrapCode.existing',
        matches: DeviceBootstrapCode(
          id: '4761f7ef-fe12-49a2-ad6b-5a4894390975',
          value: 'bootstrap-code-value',
          owner: '1528e306-7218-44c2-857c-96c3d64e29b6',
          target: const DeviceBootstrapCodeTarget(
            type: 'existing',
            device: '344f2a22-f52e-4df0-88b8-ecb42ed54478',
          ),
          expiresAt: DateTime.parse('2026-06-01T10:00:00Z'),
        ),
        fromJson: DeviceBootstrapCode.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode a new-device bootstrap code', () {
      assertInterop(
        domain: 'devices',
        resource: 'DeviceBootstrapCode.new',
        matches: DeviceBootstrapCode(
          id: '89e2d96c-d29d-4b01-bb81-8bdae16e7624',
          value: 'bootstrap-code-value',
          owner: '1528e306-7218-44c2-857c-96c3d64e29b6',
          target: const DeviceBootstrapCodeTarget(
            type: 'new',
            request: CreateDeviceOwn(name: 'new-device'),
          ),
          expiresAt: DateTime.parse('2026-06-01T10:00:00Z'),
        ),
        fromJson: DeviceBootstrapCode.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
