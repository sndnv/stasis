import 'dart:async';

import 'package:http/http.dart' as http;
import 'package:stasis_client_ui/api/api_client.dart';
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
import 'package:stasis_client_ui/model/devices/device.dart';
import 'package:stasis_client_ui/model/devices/server_state.dart';
import 'package:stasis_client_ui/model/operations/operation.dart';
import 'package:stasis_client_ui/model/operations/operation_progress.dart';
import 'package:stasis_client_ui/model/operations/operation_state.dart';
import 'package:stasis_client_ui/model/operations/rule.dart';
import 'package:stasis_client_ui/model/operations/specification_rules.dart';
import 'package:stasis_client_ui/model/schedules/active_schedule.dart';
import 'package:stasis_client_ui/model/schedules/schedule.dart';
import 'package:stasis_client_ui/model/service/init_state.dart';
import 'package:stasis_client_ui/model/users/user.dart';
import 'package:stasis_client_ui/utils/pair.dart';
import 'package:uuid/uuid.dart';

class MockApiClient extends ApiClient implements ClientApi, InitApi {
  static const String tokenApi = 'https://mock:4241';
  static const String serverApi = 'https://mock:4242';
  static const String serverCore = 'https://mock:4243';
  static String serverNode = const Uuid().v4();

  static String user = const Uuid().v4();

  static String device = const Uuid().v4();
  static String deviceNode = const Uuid().v4();

  MockApiClient() : super(server: serverApi, underlying: http.Client());

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
    return Future.value([defaultDefinition, otherDefinition]);
  }

  @override
  Future<void> deleteDatasetDefinition({required String definition}) {
    return Future.value();
  }

  @override
  Future<List<DatasetEntry>> getDatasetEntries() {
    return Future.value([defaultEntry]);
  }

  @override
  Future<List<DatasetEntry>> getDatasetEntriesForDefinition({required String definition}) {
    if (definition == defaultDefinition.id) {
      return Future.value([defaultEntry]);
    } else {
      return Future.error(Exception('Invalid definition requested: [$definition]'));
    }
  }

  @override
  Future<DatasetEntry?> getLatestDatasetEntryForDefinition({required String definition}) {
    if (definition == defaultDefinition.id) {
      return Future.value(defaultEntry);
    } else {
      return Future.value(null);
    }
  }

  @override
  Future<void> deleteDatasetEntry({required String entry}) {
    return Future.value();
  }

  @override
  Future<DatasetMetadata> getDatasetMetadata({required String entry}) {
    return Future.value(defaultMetadata);
  }

  @override
  Future<DatasetMetadataSearchResult> searchDatasetMetadata({required String searchQuery, required DateTime? until}) {
    return Future.value(const DatasetMetadataSearchResult(definitions: {}));
  }

  @override
  Future<User> getSelf() {
    return Future.value(currentUser);
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
    return Future.value(currentDevice);
  }

  @override
  Future<Map<String, ServerState>> getCurrentDeviceConnections() {
    return Future.value({serverApi: ServerState(reachable: true, timestamp: DateTime.now())});
  }

  @override
  Future<List<OperationProgress>> getOperations({required State? state}) {
    return Future.value([]);
  }

  @override
  Future<OperationState> getOperationProgress({required String operation}) {
    final now = DateTime.now();

    return Future.value(
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
          pending: {},
          processed: {},
          failed: {
            '/tmp/file/four': 'x',
          },
        ),
        metadataCollected: now,
        metadataPushed: now,
        failures: ['y', 'z'],
        completed: now,
      ),
    );
  }

  @override
  Stream<OperationState> followOperation({required String operation}) {
    return const Stream.empty();
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
  Future<Map<String, List<Rule>>> getBackupRules() {
    final Map<String, List<Rule>> rules = {
      'default': [
        const Rule(
          operation: 'include',
          directory: '/some/path',
          pattern: '*',
          comment: null,
          original: OriginalRule(line: '+ /some/path *', lineNumber: 0),
        ),
        const Rule(
          operation: 'exclude',
          directory: '/',
          pattern: 'other',
          comment: null,
          original: OriginalRule(line: '- / other', lineNumber: 1),
        ),
      ],
      '255bb999-6fba-49f0-b5bb-7c34da741872': [],
      defaultDefinition.id: [
        const Rule(
          operation: 'include',
          directory: '/a/b',
          pattern: '**',
          comment: 'Some test comment',
          original: OriginalRule(line: '+ /a/b **  # Some test comment', lineNumber: 0),
        ),
      ],
    };

    return Future.value(rules);
  }

  @override
  Future<SpecificationRules> getBackupSpecification({required String definition}) {
    final spec = SpecificationRules(
      included: ['/some/path/01', '/some/path', '/some'],
      excluded: ['/other'],
      explanation: {
        '/some/path/01': [
          const Explanation(
            operation: 'include',
            original: OriginalRule(line: '+ /some/path *', lineNumber: 0),
          ),
        ],
        '/other': [
          const Explanation(
            operation: 'exclude',
            original: OriginalRule(line: '- / other', lineNumber: 1),
          ),
        ],
      },
      unmatched: [
        Pair(const OriginalRule(line: '+ /test_01 *', lineNumber: 2), 'Not found'),
        Pair(const OriginalRule(line: '- /test_02 *', lineNumber: 3), 'Test failure'),
      ],
    );

    return Future.value(spec);
  }

  @override
  Future<OperationStarted> startBackup({required String definition}) {
    return Future.value(OperationStarted(operation: const Uuid().v4()));
  }

  @override
  Future<CreatedDatasetDefinition> defineBackup({required CreateDatasetDefinition request}) {
    return Future.value(CreatedDatasetDefinition(definition: const Uuid().v4()));
  }

  @override
  Future<void> updateBackup({required String definition, required UpdateDatasetDefinition request}) {
    return Future.value();
  }

  @override
  Future<OperationStarted> recoverUntil({
    required String definition,
    required DateTime until,
    required String? pathQuery,
    required String? destination,
    required bool? discardPaths,
  }) {
    return Future.value(OperationStarted(operation: const Uuid().v4()));
  }

  @override
  Future<OperationStarted> recoverFrom({
    required String definition,
    required String entry,
    required String? pathQuery,
    required String? destination,
    required bool? discardPaths,
  }) {
    return Future.value(OperationStarted(operation: const Uuid().v4()));
  }

  @override
  Future<OperationStarted> recoverFromLatest({
    required String definition,
    required String? pathQuery,
    required String? destination,
    required bool? discardPaths,
  }) {
    return Future.value(OperationStarted(operation: const Uuid().v4()));
  }

  @override
  Future<List<Schedule>> getPublicSchedules() {
    return Future.value([defaultSchedule]);
  }

  @override
  Future<List<ActiveSchedule>> getConfiguredSchedules() {
    return Future.value([
      ActiveSchedule(
        assignment: KeyRotationAssignment(assignmentType: 'key-rotation', schedule: defaultSchedule.id),
        schedule: null,
      ),
    ]);
  }

  @override
  Future<void> refreshConfiguredSchedules() {
    return Future.value();
  }

  @override
  Future<AnalyticsState> getAnalyticsState() {
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

    return Future.value(state);
  }

  @override
  Future<void> sendAnalyticsState() {
    return Future.value();
  }

  @override
  Future<InitState> state() {
    return Future.value(InitState.empty());
  }

  @override
  Future<void> provideCredentials({required String username, required String password}) {
    return Future.value();
  }

  static final defaultDefinition = DatasetDefinition(
    id: const Uuid().v4(),
    info: 'test-definition',
    device: device,
    redundantCopies: 42,
    existingVersions: const Retention(
      policy: Policy(policyType: 'all', versions: null),
      duration: Duration(hours: 12),
    ),
    removedVersions: const Retention(
      policy: Policy(policyType: 'all', versions: null),
      duration: Duration(hours: 366),
    ),
    created: DateTime.now().subtract(const Duration(days: 3871)),
    updated: DateTime.now().subtract(const Duration(hours: 39)),
  );

  static final otherDefinition = DatasetDefinition(
    id: const Uuid().v4(),
    info: 'other-definition',
    device: device,
    redundantCopies: 9999,
    existingVersions: const Retention(
      policy: Policy(policyType: 'all', versions: null),
      duration: Duration(hours: 12),
    ),
    removedVersions: const Retention(
      policy: Policy(policyType: 'all', versions: null),
      duration: Duration(hours: 366),
    ),
    created: DateTime.now(),
    updated: DateTime.now(),
  );

  static final defaultEntry = DatasetEntry(
    id: const Uuid().v4(),
    definition: defaultDefinition.id,
    device: device,
    data: {const Uuid().v4(), const Uuid().v4()},
    metadata: const Uuid().v4(),
    created: DateTime.now(),
  );

  static final defaultSchedule = Schedule(
    id: const Uuid().v4(),
    info: 'test-schedule',
    isPublic: true,
    start: DateTime.now(),
    interval: const Duration(hours: 12),
    nextInvocation: DateTime.now(),
    created: DateTime.now(),
    updated: DateTime.now(),
  );

  static const defaultMetadata = DatasetMetadata(
    contentChanged: {},
    metadataChanged: {},
    filesystem: FilesystemMetadata(entities: {}),
  );

  static final currentUser = User(
    id: user,
    active: true,
    limits: const UserLimits(
      maxDevices: 1024 * 1024,
      maxCrates: 1024 * 1024 * 1024,
      maxStorage: 24 * 1024 * 1024 * 1024,
      maxStoragePerCrate: 1024 * 1024 * 1024,
      maxRetention: Duration(days: 3),
      minRetention: Duration(hours: 12),
    ),
    permissions: {'a', 'b', 'c'},
    created: DateTime.now().subtract(const Duration(days: 3871)),
    updated: DateTime.now().subtract(const Duration(hours: 39)),
  );

  static final currentDevice = Device(
    id: device,
    name: 'test-device',
    node: deviceNode,
    owner: user,
    active: true,
    limits: null,
    created: DateTime.now().subtract(const Duration(days: 31 * 5)),
    updated: DateTime.now().subtract(const Duration(days: 366)),
  );
}
