import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/api/requests/create_dataset_definition.dart';
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
import 'package:stasis_client_ui/model/operations/specification_rules.dart';
import 'package:stasis_client_ui/utils/pair.dart';
import 'package:stasis_client_ui/model/schedules/active_schedule.dart';
import 'package:stasis_client_ui/model/schedules/schedule.dart';
import 'package:stasis_client_ui/model/service/init_state.dart';
import 'package:stasis_client_ui/model/users/user.dart';
import 'package:http/http.dart' as http;

class MockApiClient extends ApiClient implements InitApi, ClientApi {
  MockApiClient() : super(server: 'mock-server', underlying: http.Client());

  @override
  Future<InitState> state() {
    return Future.value(const InitState(startup: 'pending', cause: null, message: null));
  }

  @override
  Future<void> provideCredentials({required String username, required String password}) {
    return Future.value();
  }

  @override
  Future<bool> isActive() {
    return Future.value(true);
  }

  @override
  Future<void> stop() {
    return Future.value();
  }

  @override
  Future<List<DatasetDefinition>> getDatasetDefinitions() {
    const definitions = [
      DatasetDefinition(
        id: '255bb999-6fba-49f0-b5bb-7c34da741872',
        info: 'test-definition-01',
        device: 'test-device-1',
        redundantCopies: 2,
        existingVersions:
            Retention(policy: Policy(policyType: 'at-most', versions: 5), duration: Duration(seconds: 3600)),
        removedVersions: Retention(policy: Policy(policyType: 'all', versions: null), duration: Duration(seconds: 60)),
      ),
      DatasetDefinition(
        id: 'test-id-2',
        info: 'test-definition-02',
        device: 'test-device-2',
        redundantCopies: 3,
        existingVersions:
            Retention(policy: Policy(policyType: 'all', versions: null), duration: Duration(seconds: 600000)),
        removedVersions:
            Retention(policy: Policy(policyType: 'latest-only', versions: null), duration: Duration(seconds: 6000)),
      ),
      DatasetDefinition(
        id: 'test-id-3',
        info: 'test-definition-03',
        device: 'test-device-3',
        redundantCopies: 1,
        existingVersions:
            Retention(policy: Policy(policyType: 'latest-only', versions: null), duration: Duration(seconds: 60)),
        removedVersions:
            Retention(policy: Policy(policyType: 'latest-only', versions: null), duration: Duration(seconds: 60)),
      ),
    ];

    return Future.value(definitions);
  }

  @override
  Future<List<DatasetEntry>> getDatasetEntries() {
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
    return Future.value(entries);
  }

  @override
  Future<List<DatasetEntry>> getDatasetEntriesForDefinition({required String definition}) {
    if (definition == 'test-id-3') {
      return Future.value([
        DatasetEntry(
          id: 'test-id-999',
          definition: definition,
          device: 'test-device-1',
          data: {'test-id-2', 'test-id-3', 'test-id-4'},
          metadata: 'test-id',
          created: DateTime.parse('2020-10-01T01:03:01'),
        ),
      ]);
    } else if (definition != 'test-id-2') {
      return Future.value([
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
      ]);
    } else {
      return Future.value([]);
    }
  }

  @override
  Future<DatasetEntry?> getLatestDatasetEntryForDefinition({required String definition}) {
    final entry = DatasetEntry(
      id: 'test-id-5',
      definition: definition,
      device: 'test-device-1',
      data: {'test-id-6'},
      metadata: 'test-id-7',
      created: DateTime.parse('2020-10-01T01:03:02'),
    );
    return Future.value(entry);
  }

  @override
  Future<DatasetMetadata> getDatasetMetadata({required String entry}) {
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
        '/some/test-file-1': FileEntityMetadata(
          path: '/some/test-file-1',
          size: 1024 * 32,
          link: null,
          isHidden: false,
          created: DateTime.parse('2020-10-01T01:02:07'),
          updated: DateTime.parse('2020-10-01T01:02:08'),
          owner: 'test-user',
          group: 'test-group',
          permissions: '004',
          checksum: 45,
          crates: {
            '/some/test-file-1_0': 'some-id',
          },
          compression: 'none',
          entityType: 'file',
        ),
        '/some/test-file-2': FileEntityMetadata(
          path: '/some/test-file-2',
          size: 1024 * 64,
          link: null,
          isHidden: false,
          created: DateTime.parse('2020-10-01T01:02:07'),
          updated: DateTime.parse('2020-10-01T01:02:08'),
          owner: 'test-user',
          group: 'test-group',
          permissions: '004',
          checksum: 46,
          crates: {
            '/some/test-file-2_0': 'some-id',
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
          '/some/test-file-1': EntityState(entityState: 'new', entry: null),
          '/some/test-file-2': EntityState(entityState: 'new', entry: null),
        },
      ),
    );

    return Future.value(metadata);
  }

  @override
  Future<DatasetMetadataSearchResult> searchDatasetMetadata({required String searchQuery, required DateTime? until}) {
    final result = DatasetMetadataSearchResult(definitions: {
      'test-id-1': DatasetDefinitionResult(
        definitionInfo: 'test-info-1',
        entryId: 'test-entry-1',
        entryCreated: DateTime.now(),
        matches: {
          '/tmp/test-file': const EntityState(entityState: 'existing', entry: 'test-id'),
          '/tmp/test-file-1': const EntityState(entityState: 'new', entry: 'test-id'),
          '/tmp/a/b/test-file-2': const EntityState(entityState: 'upsRWS', entry: 'test-id'),
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
    return Future.value(result);
  }

  @override
  Future<User> getSelf() {
    const user = User(
      id: 'test-user-1',
      active: true,
      limits: UserLimits(
        maxDevices: 42,
        maxCrates: 999,
        maxStorage: 1000000000,
        maxStoragePerCrate: 1000000,
        maxRetention: Duration(seconds: 9000),
        minRetention: Duration(seconds: 90),
      ),
      permissions: {'A', 'B', 'C'},
    );

    return Future.value(user);
  }

  @override
  Future<void> updateOwnPassword({required UpdateUserPassword request}) {
    return Future.value();
  }

  @override
  Future<void> updateOwnSalt({required UpdateUserSalt request}) {
    return Future.value();
  }

  @override
  Future<Device> getCurrentDevice() {
    const device = Device(
      id: 'test-device-1',
      name: 'test-device-01',
      node: 'test-node',
      owner: 'test-owner',
      active: true,
      limits: DeviceLimits(
        maxCrates: 100,
        maxStorage: 5000000,
        maxStoragePerCrate: 50000,
        maxRetention: Duration(seconds: 4000),
        minRetention: Duration(seconds: 60),
      ),
    );

    return Future.value(device);
  }

  @override
  Future<Map<String, ServerState>> getCurrentDeviceConnections() {
    final connections = {
      'localhost:9090': ServerState(reachable: true, timestamp: DateTime.parse('2023-08-18T01:04:01')),
      'localhost:9091': ServerState(reachable: false, timestamp: DateTime.parse('2023-08-31T11:05:01')),
    };

    return Future.value(connections);
  }

  @override
  Future<List<OperationProgress>> getOperations({required State? state}) {
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
        type: Type.garbageCollection,
        progress: Progress(
          started: DateTime.now(),
          total: 5000,
          processed: 5000,
          failures: 15,
          completed: DateTime.now(),
        ),
      ),
    ];

    return Future.value(operations);
  }

  @override
  Future<OperationState> getOperationProgress({required String operation}) {
    final progress = BackupState(
      operation: operation,
      definition: 'test-definition',
      started: DateTime.parse('2020-12-21T01:02:00'),
      type: 'backup',
      entities: const BackupStateEntities(
        discovered: ['/some/path/01'],
        unmatched: [],
        examined: [],
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

    return Future.value(progress);
  }

  @override
  Stream<OperationState> followOperation({required String operation}) {
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
          unmatched: [],
          examined: [],
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
          collected: ['/tmp/file/one'],
          pending: {
            '/tmp/file/two': PendingSourceEntity(expectedParts: 3, processedParts: 2),
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

    return Stream.fromIterable(updates);
  }

  @override
  Future<void> stopOperation({required String operation}) {
    return Future.value();
  }

  @override
  Future<void> resumeOperation({required String operation}) {
    return Future.value();
  }

  @override
  Future<void> removeOperation({required String operation}) {
    return Future.value();
  }

  @override
  Future<SpecificationRules> getBackupRules() {
    final rules = SpecificationRules(included: [
      '/Users/angelsanadinov/repos/k8s/k8s-platform-normalizer-config/k8s-platform-normalizer-config/k8s-platform-normalizer-config/k8s-platform-normalizer-config/k8s-platform-normalizer-config/Users/angelsanadinov/repos/k8s/k8s-platform-normalizer-config//Users/angelsanadinov/repos/k8s/k8s-platform-normalizer-config/Users/angelsanadinov/repos/k8s/k8s-platform-normalizer-config/Users/angelsanadinov/repos/k8s/k8s-platform-normalizer-config',
      '/some/path/01',
      '/some/path',
      '/some'
    ], excluded: [
      '/other'
    ], explanation: {
      '/some/path/01': [
        const Explanation(operation: 'include', original: Original(line: '+ /some/path *', lineNumber: 0))
      ],
      '/other': [const Explanation(operation: 'exclude', original: Original(line: '- / other', lineNumber: 1))],
    }, unmatched: [
      Pair(const Original(line: '+ /test_01 *', lineNumber: 2), 'Not found'),
      Pair(const Original(line: '- /test_02 *', lineNumber: 3), 'Test failure'),
    ]);

    return Future.value(rules);
  }

  @override
  Future<OperationStarted> startBackup({required String definition}) {
    return Future.value(const OperationStarted(operation: 'test-operation'));
  }

  @override
  Future<CreatedDatasetDefinition> defineBackup({required CreateDatasetDefinition request}) {
    return Future.value(const CreatedDatasetDefinition(definition: 'test-definition'));
  }

  @override
  Future<OperationStarted> recoverUntil({
    required String definition,
    required DateTime until,
    required String? pathQuery,
    required String? destination,
    required bool? discardPaths,
  }) {
    return Future.value(const OperationStarted(operation: 'test-operation'));
  }

  @override
  Future<OperationStarted> recoverFrom({
    required String definition,
    required String entry,
    required String? pathQuery,
    required String? destination,
    required bool? discardPaths,
  }) {
    return Future.value(const OperationStarted(operation: 'test-operation'));
  }

  @override
  Future<OperationStarted> recoverFromLatest({
    required String definition,
    required String? pathQuery,
    required String? destination,
    required bool? discardPaths,
  }) {
    return Future.value(const OperationStarted(operation: 'test-operation'));
  }

  @override
  Future<List<Schedule>> getPublicSchedules() {
    final schedules = [
      Schedule(
        id: 'test-id-1',
        info: 'test-info-1',
        isPublic: true,
        start: DateTime.now(),
        interval: const Duration(seconds: 1),
        nextInvocation: DateTime.now(),
      ),
      Schedule(
        id: 'test-id-2',
        info: 'test-info-2',
        isPublic: true,
        start: DateTime.now(),
        interval: const Duration(seconds: 123),
        nextInvocation: DateTime.now(),
      ),
    ];
    return Future.value(schedules);
  }

  @override
  Future<List<ActiveSchedule>> getConfiguredSchedules() {
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
          schedule: 'test-id-2',
          definition: 'test-definition',
          entities: [],
        ),
        schedule: embeddedSchedule,
      ),
      ActiveSchedule(
        assignment: const ExpirationAssignment(
          assignmentType: 'expiration',
          schedule: 'test-id-2',
        ),
        schedule: embeddedSchedule,
      ),
    ];
    return Future.value(schedules);
  }

  @override
  Future<void> refreshConfiguredSchedules() {
    return Future.value();
  }
}
