import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/api/requests/create_device_own.dart';
import 'package:server_ui/model/devices/device_bootstrap_code.dart';

void main() {
  group('A DeviceBootstrapCode should', () {
    test('provide device info for existing devices', () {
      final code = DeviceBootstrapCode(
        id: 'test-id',
        value: 'test-code',
        owner: 'test-owner',
        target: DeviceBootstrapCodeTarget(type: 'existing', device: 'test-device'),
        expiresAt: DateTime.now(),
      );

      expect(DeviceBootstrapCode.extractDeviceInfo(code), 'test-device');
    });

    test('provide device info for new devices', () {
      final code = DeviceBootstrapCode(
        id: 'test-id',
        value: 'test-code',
        owner: 'test-owner',
        target: DeviceBootstrapCodeTarget(type: 'new', request: CreateDeviceOwn(name: 'test-name')),
        expiresAt: DateTime.now(),
      );

      expect(DeviceBootstrapCode.extractDeviceInfo(code), 'test-name');
    });

    test('fail to provide device info for invalid targets', () {
      final code = DeviceBootstrapCode(
        id: 'test-id',
        value: 'test-code',
        owner: 'test-owner',
        target: DeviceBootstrapCodeTarget(type: 'other'),
        expiresAt: DateTime.now(),
      );

      expect(() => DeviceBootstrapCode.extractDeviceInfo(code), throwsA(const TypeMatcher<ArgumentError>()));
    });
  });
}
