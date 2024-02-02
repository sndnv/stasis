import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:server_ui/api/default_api_client.dart';
import 'package:server_ui/model/api/requests/create_dataset_definition.dart';
import 'package:server_ui/model/api/requests/create_device_own.dart';
import 'package:server_ui/model/api/requests/create_device_privileged.dart';
import 'package:server_ui/model/api/requests/create_node.dart';
import 'package:server_ui/model/api/requests/create_schedule.dart';
import 'package:server_ui/model/api/requests/create_user.dart';
import 'package:server_ui/model/api/requests/update_dataset_definition.dart';
import 'package:server_ui/model/api/requests/update_device_limits.dart';
import 'package:server_ui/model/api/requests/update_device_state.dart';
import 'package:server_ui/model/api/requests/update_node.dart';
import 'package:server_ui/model/api/requests/update_schedule.dart';
import 'package:server_ui/model/api/requests/update_user_limits.dart';
import 'package:server_ui/model/api/requests/update_user_password.dart';
import 'package:server_ui/model/api/requests/update_user_permissions.dart';
import 'package:server_ui/model/api/requests/update_user_state.dart';
import 'package:server_ui/model/api/responses/created_dataset_definition.dart';
import 'package:server_ui/model/api/responses/created_device.dart';
import 'package:server_ui/model/api/responses/created_node.dart';
import 'package:server_ui/model/api/responses/created_schedule.dart';
import 'package:server_ui/model/api/responses/created_user.dart';
import 'package:server_ui/model/api/responses/ping.dart';
import 'package:server_ui/model/api/responses/updated_user_salt.dart';
import 'package:server_ui/model/datasets/dataset_definition.dart';
import 'package:server_ui/model/datasets/dataset_entry.dart';
import 'package:server_ui/model/devices/device.dart';
import 'package:server_ui/model/devices/device_key.dart';
import 'package:server_ui/model/manifests/manifest.dart';
import 'package:server_ui/model/nodes/crate_store_descriptor.dart';
import 'package:server_ui/model/nodes/node.dart';
import 'package:server_ui/model/reservations/crate_storage_reservation.dart';
import 'package:server_ui/model/schedules/schedule.dart';
import 'package:server_ui/model/users/user.dart';

import 'default_api_client_test.mocks.dart';

@GenerateMocks([http.Client])
void main() {
  const server = 'http://localhost:1234';
  const applicationJson = {'Content-Type': 'application/json'};

  group('A DefaultApiClient for dataset definitions should', () {
    test('retrieve dataset definitions (privileged)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const versions = Retention(policy: Policy(policyType: 'at-most', versions: 2), duration: Duration(seconds: 3));

      const definitions = [
        DatasetDefinition(
          id: 'test-id-1',
          info: 'test-info-1',
          device: 'test-device-1',
          redundantCopies: 2,
          existingVersions: versions,
          removedVersions: versions,
        ),
        DatasetDefinition(
          id: 'test-id-2',
          info: 'test-info-2',
          device: 'test-device-1',
          redundantCopies: 3,
          existingVersions: versions,
          removedVersions: versions,
        ),
      ];

      final response = jsonEncode(definitions);

      when(underlying.get(Uri.parse('$server/v1/datasets/definitions')))
          .thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getDatasetDefinitions(privileged: true), definitions);
    });

    test('retrieve dataset definitions (own)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const versions = Retention(policy: Policy(policyType: 'at-most', versions: 2), duration: Duration(seconds: 3));

      const definitions = [
        DatasetDefinition(
          id: 'test-id-1',
          info: 'test-info-1',
          device: 'test-device-1',
          redundantCopies: 2,
          existingVersions: versions,
          removedVersions: versions,
        ),
        DatasetDefinition(
          id: 'test-id-2',
          info: 'test-info-2',
          device: 'test-device-1',
          redundantCopies: 3,
          existingVersions: versions,
          removedVersions: versions,
        ),
      ];

      final response = jsonEncode(definitions);

      when(underlying.get(Uri.parse('$server/v1/datasets/definitions/own')))
          .thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getDatasetDefinitions(privileged: false), definitions);
    });

    test('create dataset definitions (privileged)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const versions = Retention(policy: Policy(policyType: 'at-most', versions: 2), duration: Duration(seconds: 3));

      const request = CreateDatasetDefinition(
        info: 'test-info-1',
        device: 'test-device-1',
        redundantCopies: 2,
        existingVersions: versions,
        removedVersions: versions,
      );

      when(underlying.post(Uri.parse('$server/v1/datasets/definitions'),
              headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('{"definition":"test-definition"}', 200));

      expect(
        await client.createDatasetDefinition(privileged: true, request: request),
        const CreatedDatasetDefinition(definition: 'test-definition'),
      );
    });

    test('create dataset definitions (own)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const versions = Retention(policy: Policy(policyType: 'at-most', versions: 2), duration: Duration(seconds: 3));

      const request = CreateDatasetDefinition(
        info: 'test-info-1',
        device: 'test-device-1',
        redundantCopies: 2,
        existingVersions: versions,
        removedVersions: versions,
      );

      when(underlying.post(Uri.parse('$server/v1/datasets/definitions/own'),
              headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('{"definition":"test-definition"}', 200));

      expect(
        await client.createDatasetDefinition(privileged: false, request: request),
        const CreatedDatasetDefinition(definition: 'test-definition'),
      );
    });

    test('update dataset definitions (privileged)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const versions = Retention(policy: Policy(policyType: 'at-most', versions: 2), duration: Duration(seconds: 3));

      const id = 'test-definition';

      const request = UpdateDatasetDefinition(
        info: 'test-info-1',
        redundantCopies: 2,
        existingVersions: versions,
        removedVersions: versions,
      );

      when(underlying.put(Uri.parse('$server/v1/datasets/definitions/$id'),
              headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(
        () async => await client.updateDatasetDefinition(privileged: true, id: id, request: request),
        returnsNormally,
      );
    });

    test('update dataset definitions (own)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const versions = Retention(policy: Policy(policyType: 'at-most', versions: 2), duration: Duration(seconds: 3));

      const id = 'test-definition';

      const request = UpdateDatasetDefinition(
        info: 'test-info-1',
        redundantCopies: 2,
        existingVersions: versions,
        removedVersions: versions,
      );

      when(underlying.put(Uri.parse('$server/v1/datasets/definitions/own/$id'),
              headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(
        () async => await client.updateDatasetDefinition(privileged: false, id: id, request: request),
        returnsNormally,
      );
    });

    test('delete dataset definitions (privileged)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-definition';

      when(underlying.delete(Uri.parse('$server/v1/datasets/definitions/$id')))
          .thenAnswer((_) async => http.Response('', 200));

      expect(
        () async => await client.deleteDatasetDefinition(privileged: true, id: id),
        returnsNormally,
      );
    });

    test('delete dataset definitions (own)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-definition';

      when(underlying.delete(Uri.parse('$server/v1/datasets/definitions/own/$id')))
          .thenAnswer((_) async => http.Response('', 200));

      expect(
        () async => await client.deleteDatasetDefinition(privileged: false, id: id),
        returnsNormally,
      );
    });
  });

  group('A DefaultApiClient for dataset entries should', () {
    test('retrieve dataset entries for a definition (privileged)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      final now = DateTime.now();

      const definition = 'test-definition-1';

      final entries = [
        DatasetEntry(
          id: 'test-id-1',
          definition: definition,
          device: 'test-device-1',
          data: {'test-data-1', 'test-data-2'},
          metadata: 'test-metadata-1',
          created: now,
        ),
        DatasetEntry(
          id: 'test-id-2',
          definition: definition,
          device: 'test-device-1',
          data: {'test-data-3', 'test-data-4'},
          metadata: 'test-metadata-2',
          created: now,
        ),
      ];

      final response = jsonEncode(entries);

      when(underlying.get(Uri.parse('$server/v1/datasets/entries/for-definition/$definition')))
          .thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getDatasetEntriesForDefinition(privileged: true, definition: definition), entries);
    });

    test('retrieve dataset entries for a definition (own)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      final now = DateTime.now();

      const definition = 'test-definition-1';

      final entries = [
        DatasetEntry(
          id: 'test-id-1',
          definition: definition,
          device: 'test-device-1',
          data: {'test-data-1', 'test-data-2'},
          metadata: 'test-metadata-1',
          created: now,
        ),
        DatasetEntry(
          id: 'test-id-2',
          definition: definition,
          device: 'test-device-1',
          data: {'test-data-3', 'test-data-4'},
          metadata: 'test-metadata-2',
          created: now,
        ),
      ];

      final response = jsonEncode(entries);

      when(underlying.get(Uri.parse('$server/v1/datasets/entries/own/for-definition/$definition')))
          .thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getDatasetEntriesForDefinition(privileged: false, definition: definition), entries);
    });

    test('delete dataset entries (privileged)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-entry';

      when(underlying.delete(Uri.parse('$server/v1/datasets/entries/$id')))
          .thenAnswer((_) async => http.Response('', 200));

      expect(
        () async => await client.deleteDatasetEntry(privileged: true, id: id),
        returnsNormally,
      );
    });

    test('delete dataset entries (own)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-entry';

      when(underlying.delete(Uri.parse('$server/v1/datasets/entries/own/$id')))
          .thenAnswer((_) async => http.Response('', 200));

      expect(
        () async => await client.deleteDatasetEntry(privileged: false, id: id),
        returnsNormally,
      );
    });
  });

  group('A DefaultApiClient for users should', () {
    test('retrieve all users', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const users = [
        User(id: 'test-user-1', salt: 'test-salt-1', active: true, permissions: {'a', 'b', 'c'}),
        User(id: 'test-user-2', salt: 'test-salt-2', active: false, permissions: {'d'}),
        User(id: 'test-user-3', salt: 'test-salt-3', active: true, permissions: {'a', 'b', 'c'}),
      ];

      final response = jsonEncode(users);

      when(underlying.get(Uri.parse('$server/v1/users'))).thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getUsers(), users);
    });

    test('create users', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const request = CreateUser(
        username: 'test-user',
        rawPassword: 'test-password',
        permissions: {'a', 'b'},
      );

      when(underlying.post(Uri.parse('$server/v1/users'), headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('{"user":"test-user-id"}', 200));

      expect(
        await client.createUser(request: request),
        const CreatedUser(user: 'test-user-id'),
      );
    });

    test('delete users', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-user';

      when(underlying.delete(Uri.parse('$server/v1/users/$id'))).thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteUser(id: id), returnsNormally);
    });

    test('update user limits', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-user';

      const request = UpdateUserLimits(
        limits: UserLimits(
          maxDevices: 1,
          maxCrates: 2,
          maxStorage: 3,
          maxStoragePerCrate: 4,
          maxRetention: Duration(seconds: 5),
          minRetention: Duration(seconds: 6),
        ),
      );

      when(underlying.put(Uri.parse('$server/v1/users/$id/limits'),
              headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.updateUserLimits(id: id, request: request), returnsNormally);
    });

    test('update user permissions', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-user';

      const request = UpdateUserPermissions(permissions: {'x', 'y'});

      when(underlying.put(Uri.parse('$server/v1/users/$id/permissions'),
              headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.updateUserPermissions(id: id, request: request), returnsNormally);
    });

    test('update user state', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-user';

      const request = UpdateUserState(active: false);

      when(underlying.put(Uri.parse('$server/v1/users/$id/state'), headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.updateUserState(id: id, request: request), returnsNormally);
    });

    test('update user passwords', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-user';

      const request = UpdateUserPassword(rawPassword: 'updated-password');

      when(underlying.put(Uri.parse('$server/v1/users/$id/password'),
              headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.updateUserPassword(id: id, request: request), returnsNormally);
    });

    test('retrieve own user information', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const self = User(id: 'test-user-1', salt: 'test-salt-1', active: true, permissions: {'a', 'b', 'c'});

      final response = jsonEncode(self);

      when(underlying.get(Uri.parse('$server/v1/users/self'))).thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getSelf(), self);
    });

    test('deactivate own user', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      when(underlying.put(Uri.parse('$server/v1/users/self/deactivate'), headers: applicationJson, body: '{}'))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deactivateSelf(), returnsNormally);
    });

    test('reset own user salt', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const salt = UpdatedUserSalt(salt: 'test-salt');

      final response = jsonEncode(salt);

      when(underlying.put(Uri.parse('$server/v1/users/self/salt')))
          .thenAnswer((_) async => http.Response(response, 200));

      expect(await client.resetOwnSalt(), salt);
    });

    test('update own user password', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const request = UpdateUserPassword(rawPassword: 'updated-password');

      when(underlying.put(Uri.parse('$server/v1/users/self/password'),
              headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.updateOwnPassword(request: request), returnsNormally);
    });
  });

  group('A DefaultApiClient for devices should', () {
    test('retrieve all devices (privileged)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const devices = [
        Device(id: 'test-id-1', name: 'test-device', owner: 'test-owner', node: 'test-node-1', active: true),
        Device(id: 'test-id-2', name: 'test-device', owner: 'test-owner', node: 'test-node-2', active: false),
        Device(id: 'test-id-3', name: 'test-device', owner: 'test-owner', node: 'test-node-3', active: true),
      ];

      final response = jsonEncode(devices);

      when(underlying.get(Uri.parse('$server/v1/devices'))).thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getDevices(privileged: true), devices);
    });

    test('retrieve all devices (own)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const devices = [
        Device(id: 'test-id-1', name: 'test-device', owner: 'test-owner', node: 'test-node-1', active: true),
        Device(id: 'test-id-2', name: 'test-device', owner: 'test-owner', node: 'test-node-2', active: false),
        Device(id: 'test-id-3', name: 'test-device', owner: 'test-owner', node: 'test-node-3', active: true),
      ];

      final response = jsonEncode(devices);

      when(underlying.get(Uri.parse('$server/v1/devices/own'))).thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getDevices(privileged: false), devices);
    });

    test('create devices (privileged)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const request = CreateDevicePrivileged(name: 'test-name', owner: 'test-owner');

      when(underlying.post(Uri.parse('$server/v1/devices'), headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('{"device":"test-device-id","node":"test-node-id"}', 200));

      expect(
        await client.createDevice(request: request),
        const CreatedDevice(device: 'test-device-id', node: 'test-node-id'),
      );
    });

    test('create devices (own)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const limits = DeviceLimits(
        maxCrates: 1,
        maxStorage: 2,
        maxStoragePerCrate: 3,
        maxRetention: Duration(seconds: 4),
        minRetention: Duration(seconds: 5),
      );

      const request = CreateDeviceOwn(name: 'test-name', limits: limits);

      when(underlying.post(Uri.parse('$server/v1/devices/own'), headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('{"device":"test-device-id","node":"test-node-id"}', 200));

      expect(
        await client.createOwnDevice(request: request),
        const CreatedDevice(device: 'test-device-id', node: 'test-node-id'),
      );
    });

    test('delete devices (privileged)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-device';

      when(underlying.delete(Uri.parse('$server/v1/devices/$id'))).thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteDevice(privileged: true, id: id), returnsNormally);
    });

    test('delete devices (own)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-device';

      when(underlying.delete(Uri.parse('$server/v1/devices/own/$id'))).thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteDevice(privileged: false, id: id), returnsNormally);
    });

    test('update device limits (privileged)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-device';

      const request = UpdateDeviceLimits(
        limits: DeviceLimits(
          maxCrates: 1,
          maxStorage: 2,
          maxStoragePerCrate: 3,
          maxRetention: Duration(seconds: 4),
          minRetention: Duration(seconds: 5),
        ),
      );

      when(underlying.put(Uri.parse('$server/v1/devices/$id/limits'),
              headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.updateDeviceLimits(privileged: true, id: id, request: request), returnsNormally);
    });

    test('update device limits (own)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-device';

      const request = UpdateDeviceLimits(
        limits: DeviceLimits(
          maxCrates: 1,
          maxStorage: 2,
          maxStoragePerCrate: 3,
          maxRetention: Duration(seconds: 4),
          minRetention: Duration(seconds: 5),
        ),
      );

      when(underlying.put(Uri.parse('$server/v1/devices/own/$id/limits'),
              headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.updateDeviceLimits(privileged: false, id: id, request: request), returnsNormally);
    });

    test('update device state (privileged)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-device';

      const request = UpdateDeviceState(active: false);

      when(underlying.put(Uri.parse('$server/v1/devices/$id/state'),
              headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.updateDeviceState(privileged: true, id: id, request: request), returnsNormally);
    });

    test('update device state (own)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-device';

      const request = UpdateDeviceState(active: false);

      when(underlying.put(Uri.parse('$server/v1/devices/own/$id/state'),
              headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.updateDeviceState(privileged: false, id: id, request: request), returnsNormally);
    });

    test('retrieve all device keys (privileged)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const deviceKeys = [
        DeviceKey(owner: 'test-owner', device: 'test-device-1'),
        DeviceKey(owner: 'test-owner', device: 'test-device-2'),
        DeviceKey(owner: 'test-owner', device: 'test-device-3'),
      ];

      final response = jsonEncode(deviceKeys);

      when(underlying.get(Uri.parse('$server/v1/devices/keys'))).thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getDeviceKeys(privileged: true), deviceKeys);
    });

    test('retrieve all device keys (own)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const deviceKeys = [
        DeviceKey(owner: 'test-owner', device: 'test-device-1'),
        DeviceKey(owner: 'test-owner', device: 'test-device-2'),
        DeviceKey(owner: 'test-owner', device: 'test-device-3'),
      ];

      final response = jsonEncode(deviceKeys);

      when(underlying.get(Uri.parse('$server/v1/devices/own/keys')))
          .thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getDeviceKeys(privileged: false), deviceKeys);
    });

    test('retrieve individual device keys (privileged)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const device = 'test-device-1';
      const deviceKey = DeviceKey(owner: 'test-owner', device: device);

      final response = jsonEncode(deviceKey);

      when(underlying.get(Uri.parse('$server/v1/devices/$device/key')))
          .thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getDeviceKey(privileged: true, forDevice: device), deviceKey);
    });

    test('retrieve individual device keys (own)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const device = 'test-device-1';
      const deviceKey = DeviceKey(owner: 'test-owner', device: device);

      final response = jsonEncode(deviceKey);

      when(underlying.get(Uri.parse('$server/v1/devices/own/$device/key')))
          .thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getDeviceKey(privileged: false, forDevice: device), deviceKey);
    });

    test('delete device keys (privileged)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const device = 'test-device';

      when(underlying.delete(Uri.parse('$server/v1/devices/$device/key')))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteDeviceKey(privileged: true, forDevice: device), returnsNormally);
    });

    test('delete device keys (own)', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const device = 'test-device';

      when(underlying.delete(Uri.parse('$server/v1/devices/own/$device/key')))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteDeviceKey(privileged: false, forDevice: device), returnsNormally);
    });
  });

  group('A DefaultApiClient for schedules should', () {
    test('retrieve all schedules', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      final now = DateTime.now();

      final devices = [
        Schedule(id: 'test-id-1', info: 'test-info', isPublic: true, start: now, interval: const Duration(seconds: 1)),
        Schedule(id: 'test-id-2', info: 'test-info', isPublic: false, start: now, interval: const Duration(seconds: 1)),
        Schedule(id: 'test-id-3', info: 'test-info', isPublic: true, start: now, interval: const Duration(seconds: 1)),
      ];

      final response = jsonEncode(devices);

      when(underlying.get(Uri.parse('$server/v1/schedules'))).thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getSchedules(), devices);
    });

    test('retrieve all public schedules', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      final now = DateTime.now();

      final devices = [
        Schedule(id: 'test-id-1', info: 'test-info', isPublic: true, start: now, interval: const Duration(seconds: 1)),
        Schedule(id: 'test-id-2', info: 'test-info', isPublic: true, start: now, interval: const Duration(seconds: 1)),
        Schedule(id: 'test-id-3', info: 'test-info', isPublic: true, start: now, interval: const Duration(seconds: 1)),
      ];

      final response = jsonEncode(devices);

      when(underlying.get(Uri.parse('$server/v1/schedules/public')))
          .thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getPublicSchedules(), devices);
    });

    test('create schedules', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      final request = CreateSchedule(
        info: 'test-info',
        isPublic: true,
        start: DateTime.now(),
        interval: const Duration(seconds: 1),
      );

      when(underlying.post(Uri.parse('$server/v1/schedules'), headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('{"schedule":"test-schedule-id"}', 200));

      expect(
        await client.createSchedule(request: request),
        const CreatedSchedule(schedule: 'test-schedule-id'),
      );
    });

    test('update schedules', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-schedule';

      final request = UpdateSchedule(info: 'test-info', start: DateTime.now(), interval: const Duration(seconds: 1));

      when(underlying.put(Uri.parse('$server/v1/schedules/$id'), headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.updateSchedule(id: id, request: request), returnsNormally);
    });

    test('delete schedules', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-schedule';

      when(underlying.delete(Uri.parse('$server/v1/schedules/$id'))).thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteSchedule(id: id), returnsNormally);
    });
  });

  group('A DefaultApiClient for nodes should', () {
    test('retrieve all nodes', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      final nodes = [
        Node.local(
          id: 'test-node-1',
          storeDescriptor: CrateStoreDescriptor.file(parentDirectory: '/tmp'),
        ),
        Node.remoteHttp(
          id: 'test-node-2',
          address: const HttpEndpointAddress(uri: 'http://localhost:12345'),
          storageAllowed: true,
        ),
        Node.remoteGrpc(
          id: 'test-node-3',
          address: const GrpcEndpointAddress(host: 'localhost', port: 12346, tlsEnabled: false),
          storageAllowed: false,
        ),
      ];

      final response = jsonEncode(nodes);

      when(underlying.get(Uri.parse('$server/v1/nodes'))).thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getNodes(), nodes);
    });

    test('create nodes', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      final request = CreateNode.local(CrateStoreDescriptor.file(parentDirectory: '/tmp'));

      when(underlying.post(Uri.parse('$server/v1/nodes'), headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('{"node":"test-node-id"}', 200));

      expect(
        await client.createNode(request: request),
        const CreatedNode(node: 'test-node-id'),
      );
    });

    test('update nodes', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-node';

      final request = UpdateNode.local(CrateStoreDescriptor.file(parentDirectory: '/tmp'));

      when(underlying.put(Uri.parse('$server/v1/nodes/$id'), headers: applicationJson, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.updateNode(id: id, request: request), returnsNormally);
    });

    test('delete nodes', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const id = 'test-node';

      when(underlying.delete(Uri.parse('$server/v1/nodes/$id'))).thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteNode(id: id), returnsNormally);
    });
  });

  group('A DefaultApiClient for manifests should', () {
    test('retrieve manifests', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const manifest = Manifest(
        crate: 'test-crate-1',
        size: 1,
        copies: 2,
        origin: 'test-origin',
        source: 'test-source',
        destinations: ['test-destination-1', 'test-destination-2'],
      );

      final response = jsonEncode(manifest);

      when(underlying.get(Uri.parse('$server/v1/manifests/${manifest.crate}')))
          .thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getManifest(crate: manifest.crate), manifest);
    });

    test('delete manifests', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const crate = 'test-crate';

      when(underlying.delete(Uri.parse('$server/v1/manifests/$crate'))).thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteManifest(crate: crate), returnsNormally);
    });
  });

  group('A DefaultApiClient for crate storage reservations should', () {
    test('retrieve reservations', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const reservations = [
        CrateStorageReservation(
          id: 'test-id-1',
          crate: 'test-crate-1',
          size: 1,
          copies: 2,
          origin: 'test-origin',
          target: 'test-target',
        ),
        CrateStorageReservation(
          id: 'test-id-2',
          crate: 'test-crate-2',
          size: 2,
          copies: 2,
          origin: 'test-origin',
          target: 'test-target',
        ),
      ];

      final response = jsonEncode(reservations);

      when(underlying.get(Uri.parse('$server/v1/reservations'))).thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getCrateStorageReservations(), reservations);
    });
  });

  group('A DefaultApiClient for service calls should', () {
    test('support ping requests', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      when(underlying.get(Uri.parse('$server/v1/service/ping')))
          .thenAnswer((_) async => http.Response('{"id":"test-id"}', 200));

      expect(await client.ping(), const Ping(id: 'test-id'));
    });
  });
}
