import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/devices/device.dart';

import '../assertions.dart';

void main() {
  final device = Device(
    id: 'f1e1ed1a-7dc2-4e9c-89fd-bab0ffbaa997',
    name: 'test-device',
    node: 'a9b7d0fb-3751-48fc-8706-9176eca571f1',
    owner: '7a1c27c2-0b3c-4f23-a68b-814084bfee7d',
    active: true,
    limits: const DeviceLimits(
      maxCrates: 100,
      maxStorage: 1099511627776,
      maxStoragePerCrate: 10737418240,
      maxRetention: Duration(seconds: 7776000),
      minRetention: Duration(seconds: 86400),
    ),
    created: DateTime.parse('2026-03-01T12:00:00Z'),
    updated: DateTime.parse('2026-03-02T13:00:00Z'),
  );

  final deviceWithoutLimits = device.copyWith(limits: null);

  group('Device interop', () {
    test('decode and re-encode a device', () {
      assertInterop(
        domain: 'devices',
        resource: 'Device',
        matches: device,
        fromJson: Device.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode a device with no limits', () {
      assertInterop(
        domain: 'devices',
        resource: 'Device.no-limits',
        matches: deviceWithoutLimits,
        fromJson: Device.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode a device with null limits', () {
      assertInterop(
        domain: 'devices',
        resource: 'Device.null-limits',
        matches: deviceWithoutLimits,
        fromJson: Device.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
