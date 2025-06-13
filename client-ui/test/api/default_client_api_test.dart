import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:stasis_client_ui/api/default_client_api.dart';
import 'package:stasis_client_ui/config/config.dart';
import 'package:stasis_client_ui/model/analytics/analytics_entry.dart';
import 'package:stasis_client_ui/model/analytics/analytics_state.dart';
import 'package:stasis_client_ui/model/api/requests/create_dataset_definition.dart';
import 'package:stasis_client_ui/model/api/requests/update_dataset_definition.dart';
import 'package:stasis_client_ui/model/api/requests/update_user_password.dart';
import 'package:stasis_client_ui/model/api/requests/update_user_salt.dart';
import 'package:stasis_client_ui/model/api/responses/created_dataset_definition.dart';
import 'package:stasis_client_ui/model/api/responses/operation_started.dart';
import 'package:stasis_client_ui/model/datasets/dataset_definition.dart';
import 'package:stasis_client_ui/model/datasets/dataset_entry.dart';
import 'package:stasis_client_ui/model/datasets/dataset_metadata.dart';
import 'package:stasis_client_ui/model/datasets/dataset_metadata_search_result.dart';
import 'package:stasis_client_ui/model/datasets/entity_metadata.dart';
import 'package:stasis_client_ui/model/devices/device.dart';
import 'package:stasis_client_ui/model/devices/server_state.dart';
import 'package:stasis_client_ui/model/operations/operation.dart';
import 'package:stasis_client_ui/model/operations/operation_progress.dart';
import 'package:stasis_client_ui/model/operations/operation_state.dart';
import 'package:stasis_client_ui/model/operations/rule.dart';
import 'package:stasis_client_ui/model/operations/specification_rules.dart';
import 'package:stasis_client_ui/model/schedules/active_schedule.dart';
import 'package:stasis_client_ui/model/schedules/schedule.dart';
import 'package:stasis_client_ui/model/service/ping.dart';
import 'package:stasis_client_ui/model/users/user.dart';
import 'package:stasis_client_ui/utils/pair.dart';

import 'default_client_api_test.mocks.dart';

@GenerateMocks([http.Client])
void main() {
  const server = 'http://localhost:1234';
  const applicationJson = {'Content-Type': 'application/json'};
  const apiToken = 'test-token';
  const authorization = {'Authorization': 'Bearer $apiToken'};

  group('A DefaultClientApi should', () {
    test('create new instances from config', () async {
      final config = Config(
        config: {
          'type': 'http',
          'http': {
            'interface': 'abc',
            'port': 1234,
            'context': {
              'enabled': true,
              'keystore': {
                'path': './test/resources/localhost.p12',
                'password': '',
              },
            },
          },
        },
      );

      final client =
          DefaultClientApi.fromConfig(config: config, apiToken: apiToken, timeout: const Duration(seconds: 5));

      expect(client.server, 'https://abc:1234');
    });

    test('fail to create new instances from invalid config', () async {
      final config = Config(
        config: {
          'type': 'other',
        },
      );

      expect(
        () => DefaultClientApi.fromConfig(config: config, apiToken: apiToken, timeout: const Duration(seconds: 5)),
        throwsA((e) => e.toString().contains('Expected [http] API but [other] found')),
      );
    });

    test('check if API is active', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      final response = jsonEncode(const Ping(id: 'test-id'));

      when(underlying.get(Uri.parse('$server/service/ping'), headers: authorization))
          .thenAnswer((_) async => http.Response(response, 200));

      expect(await client.isActive(), true);
    });

    test('send service termination requests', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      when(underlying.put(Uri.parse('$server/service/stop'), headers: authorization))
          .thenAnswer((_) async => http.Response('', 200));
      expect(() async => await client.stop(), returnsNormally);

      when(underlying.put(Uri.parse('$server/service/stop'), headers: authorization))
          .thenAnswer((_) async => http.Response('', 500));
      expect(() async => await client.stop(), returnsNormally);
    });

    test('get dataset metadata', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const entry = 'test-entry';
      final metadata = DatasetMetadata(
        contentChanged: {
          '/some/path/01': FileEntityMetadata(
            path: '/some/path/01',
            size: 1024,
            link: '/a/b/c',
            isHidden: false,
            created: DateTime.parse('2020-10-01T01:02:03'),
            updated: DateTime.parse('2020-10-01T01:02:04'),
            owner: 'test-user',
            group: 'test-group',
            permissions: '446',
            checksum: 42,
            crates: {
              '/some/path/01_0': 'some-id',
            },
            compression: 'gzip',
            entityType: 'file',
          ),
          '/some/path/02': FileEntityMetadata(
            path: '/some/path/02',
            size: 1024 * 32,
            link: null,
            isHidden: true,
            created: DateTime.parse('2020-10-01T01:02:05'),
            updated: DateTime.parse('2020-10-01T01:02:06'),
            owner: 'test-user',
            group: 'test-group',
            permissions: '456',
            checksum: 43,
            crates: {
              '/some/path/02_0': 'some-id-0',
              '/some/path/02_1': 'some-id-1',
            },
            compression: 'deflate',
            entityType: 'file',
          ),
          '/some/path/03': FileEntityMetadata(
            path: '/some/path/03',
            size: 1024 * 3,
            link: null,
            isHidden: false,
            created: DateTime.parse('2020-10-01T01:02:07'),
            updated: DateTime.parse('2020-10-01T01:02:08'),
            owner: 'test-user',
            group: 'test-group',
            permissions: '004',
            checksum: 44,
            crates: {
              '/some/path/03_0': 'some-id-0',
              '/some/path/03_1': 'some-id-1',
              '/some/path/03_2': 'some-id-2',
              '/some/path/03_3': 'some-id-3',
              '/some/path/03_4': 'some-id-4',
            },
            compression: 'none',
            entityType: 'file',
          ),
        },
        metadataChanged: {
          '/some/path/04': FileEntityMetadata(
            path: '/some/path/04',
            size: 1,
            link: '/a/b/c/d',
            isHidden: true,
            created: DateTime.parse('2020-10-01T01:02:09'),
            updated: DateTime.parse('2020-10-01T01:02:10'),
            owner: 'test-user',
            group: 'test-group',
            permissions: '444',
            checksum: 42,
            crates: {},
            compression: 'none',
            entityType: 'file',
          ),
          '/some/path': DirectoryEntityMetadata(
            path: '/some/path',
            link: '/e/f/g',
            isHidden: false,
            created: DateTime.parse('2020-10-01T01:02:11'),
            updated: DateTime.parse('2020-10-01T01:02:12'),
            owner: 'test-user',
            group: 'test-group',
            permissions: '760',
            entityType: 'directory',
          ),
          '/some': DirectoryEntityMetadata(
            path: '/some',
            link: null,
            isHidden: false,
            created: DateTime.parse('2020-10-01T01:02:13'),
            updated: DateTime.parse('2020-10-01T01:02:14'),
            owner: 'test-user',
            group: 'test-group',
            permissions: '740',
            entityType: 'directory',
          ),
        },
        filesystem: const FilesystemMetadata(
          entities: {
            '/some/path/01': EntityState(entityState: 'new', entry: null),
            '/some/path/02': EntityState(entityState: 'existing', entry: 'some-id'),
            '/some/path/03': EntityState(entityState: 'updated', entry: null),
            '/some/path/04': EntityState(entityState: 'new', entry: null),
            '/some/path': EntityState(entityState: 'new', entry: null),
            '/some': EntityState(entityState: 'new', entry: null),
          },
        ),
      );

      when(underlying.get(Uri.parse('$server/datasets/metadata/$entry'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(metadata), 200));

      expect(await client.getDatasetMetadata(entry: entry), metadata);
    });

    test('search dataset metadata', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      final result = DatasetMetadataSearchResult(definitions: {
        'test-id-1': DatasetDefinitionResult(
          definitionInfo: 'test-info-1',
          entryId: 'test-entry-1',
          entryCreated: DateTime.now(),
          matches: {
            '/tmp/test-file': const EntityState(entityState: 'existing', entry: 'test-id'),
          },
        ),
        'test-id-2': DatasetDefinitionResult(
          definitionInfo: 'test-info22',
          entryId: 'test-entry-1',
          entryCreated: DateTime.now(),
          matches: {
            '/tmp/test-file': const EntityState(entityState: 'existing', entry: 'test-id'),
          },
        ),
        'test-id-3': null,
      });

      const searchQuery = 'test.*';

      final until = DateTime(2020, 2, 2, 2, 2, 2);

      when(underlying.get(Uri.parse('$server/datasets/metadata/search?query=$searchQuery&until=2020-02-02T01:02:02Z'),
              headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(result), 200));

      when(underlying.get(Uri.parse('$server/datasets/metadata/search?query=$searchQuery&until=2020-02-02T02:02:02Z'),
              headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(result), 200));

      expect(await client.searchDatasetMetadata(searchQuery: searchQuery, until: until), result);
    });

    test('get dataset definitions', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      final definitions = [
        DatasetDefinition(
          id: 'test-id-1',
          info: 'test-definition-01',
          device: 'test-device-1',
          redundantCopies: 2,
          existingVersions:
              const Retention(policy: Policy(policyType: 'at-most', versions: 5), duration: Duration(seconds: 3600)),
          removedVersions:
              const Retention(policy: Policy(policyType: 'all', versions: null), duration: Duration(seconds: 60)),
          created: DateTime.parse('2020-10-01T01:03:01'),
          updated: DateTime.parse('2020-10-01T01:03:02'),
        ),
        DatasetDefinition(
          id: 'test-id-2',
          info: 'test-definition-02',
          device: 'test-device-2',
          redundantCopies: 3,
          existingVersions:
              const Retention(policy: Policy(policyType: 'all', versions: null), duration: Duration(seconds: 600000)),
          removedVersions: const Retention(
              policy: Policy(policyType: 'latest-only', versions: null), duration: Duration(seconds: 6000)),
          created: DateTime.parse('2020-10-01T01:03:01'),
          updated: DateTime.parse('2020-10-01T01:03:02'),
        ),
        DatasetDefinition(
          id: 'test-id-13',
          info: 'test-definition-03',
          device: 'test-device-3',
          redundantCopies: 1,
          existingVersions: const Retention(
              policy: Policy(policyType: 'latest-only', versions: null), duration: Duration(seconds: 60)),
          removedVersions: const Retention(
              policy: Policy(policyType: 'latest-only', versions: null), duration: Duration(seconds: 60)),
          created: DateTime.parse('2020-10-01T01:03:01'),
          updated: DateTime.parse('2020-10-01T01:03:02'),
        ),
      ];

      when(underlying.get(Uri.parse('$server/datasets/definitions'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(definitions), 200));

      expect(await client.getDatasetDefinitions(), definitions);
    });

    test('delete dataset definitions', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const definition = 'test-definition-1';

      when(underlying.delete(Uri.parse('$server/datasets/definitions/$definition'), headers: authorization))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteDatasetDefinition(definition: definition), returnsNormally);
    });

    test('get dataset entries', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      final entries = [
        DatasetEntry(
          id: 'test-id-1',
          definition: 'test-definition-1',
          device: 'test-device-1',
          data: {'test-id-2', 'test-id-3', 'test-id-4'},
          metadata: 'test-id',
          created: DateTime.parse('2020-10-01T01:03:01'),
        ),
        DatasetEntry(
          id: 'test-id-5',
          definition: 'test-definition-1',
          device: 'test-device-1',
          data: {'test-id-6'},
          metadata: 'test-id-7',
          created: DateTime.parse('2020-10-01T01:03:02'),
        ),
        DatasetEntry(
          id: 'test-id-8',
          definition: 'test-definition-1',
          device: 'test-device-1',
          data: {'test-id-9', 'test-id-10'},
          metadata: 'test-id-11',
          created: DateTime.parse('2020-10-01T01:03:03'),
        ),
        DatasetEntry(
          id: 'test-id-12',
          definition: 'test-definition-2',
          device: 'test-device-1',
          data: {'test-id-13'},
          metadata: 'test-id-14',
          created: DateTime.parse('2020-10-01T01:03:04'),
        ),
      ];

      when(underlying.get(Uri.parse('$server/datasets/entries'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(entries), 200));

      expect(await client.getDatasetEntries(), entries);
    });

    test('get dataset entries for definition', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const definition = 'test-definition-1';

      final entries = [
        DatasetEntry(
          id: 'test-id-1',
          definition: definition,
          device: 'test-device-1',
          data: {'test-id-2', 'test-id-3', 'test-id-4'},
          metadata: 'test-id',
          created: DateTime.parse('2020-10-01T01:03:01'),
        ),
        DatasetEntry(
          id: 'test-id-5',
          definition: definition,
          device: 'test-device-1',
          data: {'test-id-6'},
          metadata: 'test-id-7',
          created: DateTime.parse('2020-10-01T01:03:02'),
        ),
      ];

      when(underlying.get(Uri.parse('$server/datasets/entries/for-definition/$definition'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(entries), 200));

      expect(await client.getDatasetEntriesForDefinition(definition: definition), entries);
    });

    test('get latest dataset entry for definition', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const definition1 = 'test-definition-1';
      const definition2 = 'test-definition-1';

      final entry = DatasetEntry(
        id: 'test-id-1',
        definition: definition1,
        device: 'test-device-1',
        data: {'test-id-2', 'test-id-3', 'test-id-4'},
        metadata: 'test-id',
        created: DateTime.parse('2020-10-01T01:03:01'),
      );

      when(underlying.get(Uri.parse('$server/datasets/entries/for-definition/$definition1/latest'),
              headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(entry), 200));

      expect(await client.getLatestDatasetEntryForDefinition(definition: definition1), entry);

      when(underlying.get(Uri.parse('$server/datasets/entries/for-definition/$definition2/latest'),
              headers: authorization))
          .thenAnswer((_) async => http.Response('', 404));

      expect(await client.getLatestDatasetEntryForDefinition(definition: definition2), null);
    });

    test('delete dataset entries', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const entry = 'test-entry-1';

      when(underlying.delete(Uri.parse('$server/datasets/entries/$entry'), headers: authorization))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteDatasetEntry(entry: entry), returnsNormally);
    });

    test('get current user', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      final user = User(
        id: 'test-user-1',
        active: true,
        limits: const UserLimits(
          maxDevices: 42,
          maxCrates: 999,
          maxStorage: 1000000000,
          maxStoragePerCrate: 1000000,
          maxRetention: Duration(seconds: 9000),
          minRetention: Duration(seconds: 90),
        ),
        permissions: {'A', 'B', 'C'},
        created: DateTime.parse('2020-10-01T01:03:01'),
        updated: DateTime.parse('2020-10-01T01:03:02'),
      );

      when(underlying.get(Uri.parse('$server/user'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(user), 200));

      expect(await client.getSelf(), user);
    });

    test('update the current user password', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const request = UpdateUserPassword(currentPassword: 'old-password', newPassword: 'new-password');

      when(underlying.put(Uri.parse('$server/user/password'),
              headers: {...applicationJson, ...authorization}, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.updateOwnPassword(request: request), returnsNormally);
    });

    test('update the current user salt', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const request = UpdateUserSalt(currentPassword: 'test-password', newSalt: 'new-salt');

      when(underlying.put(Uri.parse('$server/user/salt'),
              headers: {...applicationJson, ...authorization}, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.updateOwnSalt(request: request), returnsNormally);
    });

    test('get current device', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      final device = Device(
        id: 'test-device-1',
        name: 'test-device-01',
        node: 'test-node',
        owner: 'test-owner',
        active: true,
        limits: const DeviceLimits(
          maxCrates: 100,
          maxStorage: 5000000,
          maxStoragePerCrate: 50000,
          maxRetention: Duration(seconds: 4000),
          minRetention: Duration(seconds: 60),
        ),
        created: DateTime.parse('2020-10-01T01:03:01'),
        updated: DateTime.parse('2020-10-01T01:03:02'),
      );

      when(underlying.get(Uri.parse('$server/device'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(device), 200));

      expect(await client.getCurrentDevice(), device);
    });

    test('get device connections', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      final connections = {
        'localhost:9090': ServerState(reachable: true, timestamp: DateTime.parse('2020-10-01T01:04:01')),
        'localhost:9091': ServerState(reachable: false, timestamp: DateTime.parse('2020-10-01T01:05:01')),
      };

      when(underlying.get(Uri.parse('$server/device/connections'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(connections), 200));

      expect(await client.getCurrentDeviceConnections(), connections);
    });

    test('get active operations', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      final operations = [
        OperationProgress(
          operation: 'test-operation-1',
          isActive: true,
          type: Type.backup,
          progress: Progress(
            started: DateTime.now(),
            total: 5,
            processed: 2,
            failures: 2,
            completed: null,
          ),
        ),
        OperationProgress(
          operation: 'test-operation-2',
          isActive: true,
          type: Type.expiration,
          progress: Progress(
            started: DateTime.now(),
            total: 3,
            processed: 0,
            failures: 0,
            completed: null,
          ),
        ),
      ];

      when(underlying.get(Uri.parse('$server/operations?state=active'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(operations), 200));

      expect(await client.getOperations(state: State.active), operations);
    });

    test('get completed operations', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      final operations = [
        OperationProgress(
          operation: 'test-operation-3',
          isActive: false,
          type: Type.validation,
          progress: Progress(
            started: DateTime.now(),
            total: 0,
            processed: 0,
            failures: 0,
            completed: DateTime.parse('2020-10-01T01:04:01'),
          ),
        ),
      ];

      when(underlying.get(Uri.parse('$server/operations?state=completed'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(operations), 200));

      expect(await client.getOperations(state: State.completed), operations);
    });

    test('get all operations', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      final operations = [
        OperationProgress(
          operation: 'test-operation-1',
          isActive: false,
          type: Type.backup,
          progress: Progress(
            started: DateTime.now(),
            total: 5,
            processed: 2,
            failures: 2,
            completed: null,
          ),
        ),
        OperationProgress(
          operation: 'test-operation-2',
          isActive: false,
          type: Type.expiration,
          progress: Progress(
            started: DateTime.now(),
            total: 3,
            processed: 0,
            failures: 0,
            completed: null,
          ),
        ),
        OperationProgress(
          operation: 'test-operation-3',
          isActive: true,
          type: Type.validation,
          progress: Progress(
            started: DateTime.now(),
            total: 0,
            processed: 0,
            failures: 0,
            completed: DateTime.parse('2020-10-01T01:04:01'),
          ),
        ),
      ];

      when(underlying.get(Uri.parse('$server/operations?state=all'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(operations), 200));

      expect(await client.getOperations(state: State.all), operations);
    });

    test('get operation progress', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const operation = 'test-operation-id';

      final progress = BackupState(
        operation: operation,
        definition: 'test-definition',
        started: DateTime.parse('2020-12-21T01:02:00'),
        type: 'backup',
        entities: const BackupStateEntities(
          discovered: ['/some/path/01'],
          unmatched: [],
          examined: [],
          skipped: [],
          collected: [],
          pending: {},
          processed: {},
          failed: {},
        ),
        metadataCollected: null,
        metadataPushed: null,
        failures: [],
        completed: null,
      );

      when(underlying.get(Uri.parse('$server/operations/$operation/progress'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(progress), 200));

      expect(await client.getOperationProgress(operation: operation), progress);
    });

    test('follow operation progress', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const operation = 'test-operation-id';

      final now = DateTime.now();

      final updates = [
        BackupState(
          operation: operation,
          type: 'backup',
          definition: 'test-definition',
          started: now,
          entities: const BackupStateEntities(
            discovered: [],
            unmatched: [],
            examined: [],
            skipped: [],
            collected: [],
            pending: {},
            processed: {},
            failed: {},
          ),
          metadataCollected: null,
          metadataPushed: null,
          failures: [],
          completed: null,
        ),
        BackupState(
          operation: operation,
          type: 'backup',
          definition: 'test-definition',
          started: now,
          entities: const BackupStateEntities(
            discovered: ['/tmp/file/one'],
            unmatched: ['a', 'b', 'c'],
            examined: ['/tmp/file/two'],
            skipped: ['/tmp/file/three'],
            collected: ['/tmp/file/one'],
            pending: {
              '/tmp/file/two': PendingSourceEntity(expectedParts: 1, processedParts: 2),
            },
            processed: {
              '/tmp/file/one': ProcessedSourceEntity(expectedParts: 1, processedParts: 1),
              '/tmp/file/two': ProcessedSourceEntity(expectedParts: 0, processedParts: 0),
            },
            failed: {
              '/tmp/file/four': 'x',
            },
          ),
          metadataCollected: now,
          metadataPushed: now,
          failures: ['y', 'z'],
          completed: now,
        ),
      ];

      final responseStream = Stream.fromIterable(updates)
          .map((state) => jsonEncode(state.toJson()))
          .map((json) => ['id: 1234', 'event: update', 'data: $json', '\n'].join('\n'))
          .transform(utf8.encoder);

      when(underlying.send(any)).thenAnswer((_) async => http.StreamedResponse(responseStream, 200));

      final result = await client.followOperation(operation: operation).toList();

      expect(result, updates);
    });

    test('stop an active operation', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const operation = 'test-operation-id';

      when(underlying.put(Uri.parse('$server/operations/$operation/stop'), headers: authorization))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.stopOperation(operation: operation), returnsNormally);
    });

    test('resume an inactive operation', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const operation = 'test-operation-id';

      when(underlying.put(Uri.parse('$server/operations/$operation/resume'), headers: authorization))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.resumeOperation(operation: operation), returnsNormally);
    });

    test('remove an inactive operation', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const operation = 'test-operation-id';

      when(underlying.delete(Uri.parse('$server/operations/$operation'), headers: authorization))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.removeOperation(operation: operation), returnsNormally);
    });

    test('get backup rules', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const rules = {
        'default': [
          Rule(
            operation: 'include',
            directory: '/some/path',
            pattern: '*',
            comment: null,
            original: OriginalRule(line: '+ /some/path *', lineNumber: 0),
          ),
          Rule(
            operation: 'exclude',
            directory: '/',
            pattern: 'other',
            comment: null,
            original: OriginalRule(line: '- / other', lineNumber: 1),
          ),
        ],
        '255bb999-6fba-49f0-b5bb-7c34da741872': [
          Rule(
            operation: 'include',
            directory: '/a/b',
            pattern: '**',
            comment: 'Some test comment',
            original: OriginalRule(line: '+ /a/b **  # Some test comment', lineNumber: 0),
          ),
        ],
      };

      when(underlying.get(Uri.parse('$server/operations/backup/rules'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(rules), 200));

      expect(await client.getBackupRules(), rules);
    });

    test('get backup specification', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const definition = 'test-definition';

      final spec = SpecificationRules(included: [
        '/some/path/01',
        '/some/path',
        '/some'
      ], excluded: [
        '/other'
      ], explanation: {
        '/some/path/01': [
          const Explanation(operation: 'include', original: OriginalRule(line: '+ /some/path *', lineNumber: 0))
        ],
        '/other': [const Explanation(operation: 'exclude', original: OriginalRule(line: '- / other', lineNumber: 1))],
      }, unmatched: [
        Pair(const OriginalRule(line: '+ /test_01 *', lineNumber: 2), 'Not found'),
        Pair(const OriginalRule(line: '- /test_02 *', lineNumber: 3), 'Test failure'),
      ]);

      when(underlying.get(Uri.parse('$server/operations/backup/rules/$definition/specification'),
              headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(spec), 200));

      expect(await client.getBackupSpecification(definition: definition), spec);
    });

    test('start backups', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const definition = 'test-definition';

      const started = OperationStarted(operation: 'test-operation');

      when(underlying.put(Uri.parse('$server/operations/backup/$definition'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(started), 201));

      expect(await client.startBackup(definition: definition), started);
    });

    test('define backups', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const request = CreateDatasetDefinition(
        info: 'test-info',
        device: 'test-device',
        redundantCopies: 1,
        existingVersions: Retention(
          policy: Policy(policyType: 'all', versions: null),
          duration: Duration(seconds: 2),
        ),
        removedVersions: Retention(
          policy: Policy(policyType: 'at-most', versions: 3),
          duration: Duration(seconds: 4),
        ),
      );

      const response = CreatedDatasetDefinition(definition: 'test-definition');

      when(underlying.post(Uri.parse('$server/datasets/definitions'),
              headers: {...applicationJson, ...authorization}, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response(jsonEncode(response), 200));

      expect(await client.defineBackup(request: request), response);
    });

    test('update backups', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const definition = 'test-definition-1';

      const request = UpdateDatasetDefinition(
        info: 'test-info',
        redundantCopies: 1,
        existingVersions: Retention(
          policy: Policy(policyType: 'all', versions: null),
          duration: Duration(seconds: 2),
        ),
        removedVersions: Retention(
          policy: Policy(policyType: 'at-most', versions: 3),
          duration: Duration(seconds: 4),
        ),
      );

      when(underlying.put(Uri.parse('$server/datasets/definitions/$definition'),
              headers: {...applicationJson, ...authorization}, body: jsonEncode(request)))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.updateBackup(definition: definition, request: request), returnsNormally);
    });

    test('recover until timestamp (without parameters)', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const definition = 'test-definition';

      const started = OperationStarted(operation: 'test-operation');

      const until = '2020-02-02T02:02:02Z';

      when(underlying.put(Uri.parse('$server/operations/recover/$definition/until/$until'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(started), 201));

      expect(
        await client.recoverUntil(
          definition: definition,
          until: DateTime.parse(until),
          pathQuery: null,
          destination: null,
          discardPaths: null,
        ),
        started,
      );
    });

    test('recover until timestamp (with parameters)', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const definition = 'test-definition';

      const started = OperationStarted(operation: 'test-operation');

      const until = '2020-02-02T02:02:02Z';

      when(underlying.put(
        Uri.parse('$server/operations/recover/$definition/until/$until?query=a&destination=b&keep_structure=false'),
        headers: authorization,
      )).thenAnswer((_) async => http.Response(jsonEncode(started), 201));

      expect(
        await client.recoverUntil(
          definition: definition,
          until: DateTime.parse(until),
          pathQuery: 'a',
          destination: 'b',
          discardPaths: true,
        ),
        started,
      );
    });

    test('recover from entry', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const definition = 'test-definition';

      const started = OperationStarted(operation: 'test-operation');

      const entry = 'test-entry';

      when(underlying.put(
        Uri.parse('$server/operations/recover/$definition/from/$entry?query=a&destination=b&keep_structure=false'),
        headers: authorization,
      )).thenAnswer((_) async => http.Response(jsonEncode(started), 201));

      expect(
        await client.recoverFrom(
          definition: definition,
          entry: entry,
          pathQuery: 'a',
          destination: 'b',
          discardPaths: true,
        ),
        started,
      );
    });

    test('recover from latest entry', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      const definition = 'test-definition';

      const started = OperationStarted(operation: 'test-operation');

      when(underlying.put(
        Uri.parse('$server/operations/recover/$definition/latest?query=a&destination=b&keep_structure=false'),
        headers: authorization,
      )).thenAnswer((_) async => http.Response(jsonEncode(started), 201));

      expect(
        await client.recoverFromLatest(
          definition: definition,
          pathQuery: 'a',
          destination: 'b',
          discardPaths: true,
        ),
        started,
      );
    });

    test('get public schedules', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      final schedules = [
        Schedule(
          id: 'test-id-1',
          info: 'test-info',
          isPublic: true,
          start: DateTime.now(),
          interval: const Duration(seconds: 1),
          nextInvocation: DateTime.now(),
          created: DateTime.parse('2020-10-01T01:03:01'),
          updated: DateTime.parse('2020-10-01T01:03:02'),
        ),
        Schedule(
          id: 'test-id-2',
          info: 'test-info',
          isPublic: true,
          start: DateTime.now(),
          interval: const Duration(seconds: 1),
          nextInvocation: DateTime.now(),
          created: DateTime.parse('2020-10-01T01:03:01'),
          updated: DateTime.parse('2020-10-01T01:03:02'),
        ),
      ];

      when(underlying.get(Uri.parse('$server/schedules/public'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(schedules), 200));

      expect(await client.getPublicSchedules(), schedules);
    });

    test('get configured schedules', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      final embeddedSchedule = EmbeddedSchedule(
        id: 'test-id-2',
        info: 'test-info',
        isPublic: true,
        start: DateTime.now(),
        interval: const Duration(seconds: 1),
        nextInvocation: DateTime.now(),
        retrieval: 'successful',
        message: null,
      );

      final schedules = [
        ActiveSchedule(
          assignment: const BackupAssignment(
            assignmentType: 'backup',
            schedule: 'schedule-1',
            definition: 'test-definition',
            entities: [],
          ),
          schedule: embeddedSchedule,
        ),
        ActiveSchedule(
          assignment: const ExpirationAssignment(
            assignmentType: 'expiration',
            schedule: 'schedule-2',
          ),
          schedule: embeddedSchedule,
        ),
      ];

      when(underlying.get(Uri.parse('$server/schedules/configured'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(schedules), 200));

      expect(await client.getConfiguredSchedules(), schedules);
    });

    test('refresh configured schedules', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      when(underlying.put(Uri.parse('$server/schedules/configured/refresh'), headers: authorization))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.refreshConfiguredSchedules(), returnsNormally);
    });

    test('get latest analytics state', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      final state = AnalyticsState(
        entry: AnalyticsEntry(
          runtime: RuntimeInformation(
            id: 'test-id',
            app: 'a;b;42',
            jre: 'a;b',
            os: 'a;b;c',
          ),
          events: [
            Event(id: 0, event: 'test-event-1'),
            Event(id: 0, event: 'test-event-2'),
          ],
          failures: [
            Failure(timestamp: DateTime.now(), message: 'Test failure'),
          ],
          created: DateTime.now(),
          updated: DateTime.now(),
        ),
        lastCached: DateTime.now(),
        lastTransmitted: DateTime.now(),
      );

      when(underlying.get(Uri.parse('$server/service/analytics'), headers: authorization))
          .thenAnswer((_) async => http.Response(jsonEncode(state), 200));

      expect(await client.getAnalyticsState(), state);
    });

    test('send latest analytics state', () async {
      final underlying = MockClient();
      final client = DefaultClientApi(server: server, underlying: underlying, apiToken: apiToken);

      when(underlying.put(Uri.parse('$server/service/analytics/send'), headers: authorization))
          .thenAnswer((_) async => http.Response('', 201));

      expect(() async => await client.sendAnalyticsState(), returnsNormally);
    });
  });
}
