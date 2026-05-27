import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/api/requests/create_device_own.dart';
import 'package:server_ui/model/api/requests/create_device_privileged.dart';
import 'package:server_ui/model/api/responses/created_device.dart';
import 'package:server_ui/model/devices/device.dart';

import '../assertions.dart';

void main() {
  const limits = DeviceLimits(
    maxCrates: 100,
    maxStorage: 1099511627776,
    maxStoragePerCrate: 10737418240,
    maxRetention: Duration(seconds: 7776000),
    minRetention: Duration(seconds: 86400),
  );

  group('CreateDevice interop', () {
    test('decode and re-encode CreateDeviceOwn', () {
      assertInterop(
        domain: 'devices',
        resource: 'CreateDeviceOwn',
        matches: const CreateDeviceOwn(name: 'test-device', limits: limits),
        fromJson: CreateDeviceOwn.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode CreateDevicePrivileged', () {
      assertInterop(
        domain: 'devices',
        resource: 'CreateDevicePrivileged',
        matches: const CreateDevicePrivileged(
          name: 'test-device',
          node: 'c1ed6cb7-596b-468c-968c-e4772356d948',
          owner: '1528e306-7218-44c2-857c-96c3d64e29b6',
          limits: limits,
        ),
        fromJson: CreateDevicePrivileged.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode CreatedDevice', () {
      assertInterop(
        domain: 'devices',
        resource: 'CreatedDevice',
        matches: const CreatedDevice(
          device: '344f2a22-f52e-4df0-88b8-ecb42ed54478',
          node: '6566529b-1ddf-441d-aa2c-eab318f83bcf',
        ),
        fromJson: CreatedDevice.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
