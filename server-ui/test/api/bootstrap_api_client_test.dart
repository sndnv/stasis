import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:server_ui/api/bootstrap_api_client.dart';
import 'package:server_ui/model/api/requests/create_device_own.dart';
import 'package:server_ui/model/devices/device_bootstrap_code.dart';

import 'bootstrap_api_client_test.mocks.dart';

@GenerateMocks([http.Client])
void main() {
  const server = 'http://localhost:1234';

  group('A BootstrapApiClient should', () {
    test('retrieve bootstrap codes (privileged)', () async {
      final underlying = MockClient();
      final client = BootstrapApiClient(server: server, underlying: underlying);

      final now = DateTime.now();
      final codes = [
        DeviceBootstrapCode.withDevice(
          id: 'test-id',
          value: 'test-value-1',
          owner: 'test-owner',
          device: 'test-device-1',
          expiresAt: now,
        ),
        DeviceBootstrapCode.withDevice(
          id: 'test-id',
          value: 'test-value-2',
          owner: 'test-owner',
          device: 'test-device-2',
          expiresAt: now,
        ),
        DeviceBootstrapCode.withRequest(
          id: 'test-id',
          value: 'test-value-3',
          owner: 'test-owner',
          request: CreateDeviceOwn(name: 'test-name'),
          expiresAt: now,
        ),
      ];

      final response = jsonEncode(codes);

      when(underlying.get(Uri.parse('$server/v1/devices/codes'))).thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getBootstrapCodes(privileged: true), codes);
    });

    test('retrieve bootstrap codes (own)', () async {
      final underlying = MockClient();
      final client = BootstrapApiClient(server: server, underlying: underlying);

      final now = DateTime.now();
      final codes = [
        DeviceBootstrapCode.withDevice(
          id: 'test-id',
          value: 'test-value-1',
          owner: 'test-owner',
          device: 'test-device-1',
          expiresAt: now,
        ),
        DeviceBootstrapCode.withDevice(
          id: 'test-id',
          value: 'test-value-2',
          owner: 'test-owner',
          device: 'test-device-2',
          expiresAt: now,
        ),
        DeviceBootstrapCode.withRequest(
          id: 'test-id',
          value: 'test-value-3',
          owner: 'test-owner',
          request: CreateDeviceOwn(name: 'test-name'),
          expiresAt: now,
        ),
      ];

      final response = jsonEncode(codes);

      when(underlying.get(Uri.parse('$server/v1/devices/codes/own')))
          .thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getBootstrapCodes(privileged: false), codes);
    });

    test('delete bootstrap codes (privileged)', () async {
      final underlying = MockClient();
      final client = BootstrapApiClient(server: server, underlying: underlying);

      const code = 'test-code';

      when(underlying.delete(Uri.parse('$server/v1/devices/codes/$code')))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteBootstrapCode(privileged: true, code: code), returnsNormally);
    });

    test('delete bootstrap codes (own)', () async {
      final underlying = MockClient();
      final client = BootstrapApiClient(server: server, underlying: underlying);

      const code = 'test-code';

      when(underlying.delete(Uri.parse('$server/v1/devices/codes/own/$code')))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteBootstrapCode(privileged: false, code: code), returnsNormally);
    });

    test('generate bootstrap codes (own)', () async {
      final underlying = MockClient();
      final client = BootstrapApiClient(server: server, underlying: underlying);

      const device = 'test-device';

      final code = DeviceBootstrapCode.withDevice(
        id: 'test-id',
        value: 'test-value-1',
        owner: 'test-owner',
        device: 'test-device-1',
        expiresAt: DateTime.now(),
      );

      when(underlying.put(Uri.parse('$server/v1/devices/codes/own/for-device/$device')))
          .thenAnswer((_) async => http.Response(jsonEncode(code), 200));

      expect(await client.generateBootstrapCode(forDevice: device), code);
    });
  });
}
