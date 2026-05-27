import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/api/requests/update_device_limits.dart';
import 'package:server_ui/model/api/requests/update_device_state.dart';
import 'package:server_ui/model/devices/device.dart';

import '../assertions.dart';

void main() {
  group('UpdateDevice interop', () {
    test('decode and re-encode UpdateDeviceLimits', () {
      assertInterop(
        domain: 'devices',
        resource: 'UpdateDeviceLimits',
        matches: const UpdateDeviceLimits(
          limits: DeviceLimits(
            maxCrates: 200,
            maxStorage: 2199023255552,
            maxStoragePerCrate: 21474836480,
            maxRetention: Duration(seconds: 15552000),
            minRetention: Duration(seconds: 172800),
          ),
        ),
        fromJson: UpdateDeviceLimits.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode UpdateDeviceState', () {
      assertInterop(
        domain: 'devices',
        resource: 'UpdateDeviceState',
        matches: const UpdateDeviceState(active: false),
        fromJson: UpdateDeviceState.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
