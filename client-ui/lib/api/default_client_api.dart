import 'dart:convert';
import 'dart:io';

import 'package:http/io_client.dart' as io_client;
import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/api/endpoint_context.dart';
import 'package:stasis_client_ui/config/config.dart';
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
import 'package:stasis_client_ui/model/service/ping.dart';
import 'package:stasis_client_ui/model/users/user.dart';

class DefaultClientApi extends ApiClient implements ClientApi {
  DefaultClientApi({
    required super.server,
    required super.underlying,
    required String apiToken,
  }) : super(token: apiToken);

  static DefaultClientApi fromConfig({
    required Config config,
    required String apiToken,
    required Duration timeout,
    bool insecure = false,
  }) {
    final apiType = config.getString('type');
    if (apiType != 'http') {
      throw Exception('Expected [http] API but [$apiType] found');
    }

    final apiConfig = config.getConfig('http');

    final contextEnabled = apiConfig.getBoolean('context.enabled');
    final scheme = (contextEnabled || insecure) ? 'https' : 'http';
    final interface = apiConfig.getString('interface');
    final port = apiConfig.getInt('port');
    final apiUrl = '$scheme://$interface:$port';

    final context = (contextEnabled && !insecure)
        ? CustomHttpsContext(
            certificatePath: apiConfig.getString('context.keystore.path'),
            certificatePassword: apiConfig.getString('context.keystore.password'),
          )
        : DefaultHttpsContext();

    final underlying = io_client.IOClient(
      HttpClient(context: context.get())
        ..badCertificateCallback = (X509Certificate cert, String actualHost, int actualPort) {
          return actualHost == interface && actualPort == port && cert.subject == '/CN=$interface';
        }
        ..connectionTimeout = timeout,
    );

    return DefaultClientApi(server: apiUrl, underlying: underlying, apiToken: apiToken);
  }

  @override
  Future<bool> isActive() async {
    const path = '/service/ping';
    return (await getOne(from: path, fromJson: Ping.fromJson)).id.isNotEmpty;
  }

  @override
  Future<void> stop() async {
    try {
      const path = '/service/stop';
      return await put(data: {}, to: path);
    } catch (e) {
      return Future.value();
    }
  }

  @override
  Future<DatasetMetadata> getDatasetMetadata({required String entry}) async {
    final path = '/datasets/metadata/$entry';
    return await getOne(from: path, fromJson: DatasetMetadata.fromJson);
  }

  @override
  Future<DatasetMetadataSearchResult> searchDatasetMetadata({
    required String searchQuery,
    required DateTime? until,
  }) async {
    final searchPath = '/datasets/metadata/search?query=$searchQuery';
    final fullPath =
        until == null ? searchPath : '$searchPath&until=${until.toUtc().toIso8601String().split('.').first}Z';
    return await getOne(from: fullPath, fromJson: DatasetMetadataSearchResult.fromJson);
  }

  @override
  Future<List<DatasetDefinition>> getDatasetDefinitions() async {
    const path = '/datasets/definitions';
    return await get(from: path, fromJson: DatasetDefinition.fromJson);
  }

  @override
  Future<void> deleteDatasetDefinition({required String definition}) async {
    final path = '/datasets/definitions/$definition';
    return await delete(from: path);
  }

  @override
  Future<List<DatasetEntry>> getDatasetEntries() async {
    const path = '/datasets/entries';
    return await get(from: path, fromJson: DatasetEntry.fromJson);
  }

  @override
  Future<List<DatasetEntry>> getDatasetEntriesForDefinition({required String definition}) async {
    final path = '/datasets/entries/for-definition/$definition';
    return await get(from: path, fromJson: DatasetEntry.fromJson);
  }

  @override
  Future<DatasetEntry?> getLatestDatasetEntryForDefinition({required String definition}) async {
    final path = '/datasets/entries/for-definition/$definition/latest';
    return await getOptional(from: path, fromJson: DatasetEntry.fromJson);
  }

  @override
  Future<void> deleteDatasetEntry({required String entry}) async {
    final path = '/datasets/entries/$entry';
    return await delete(from: path);
  }

  @override
  Future<User> getSelf() async {
    const path = '/user';
    return await getOne(from: path, fromJson: User.fromJson);
  }

  @override
  Future<void> updateOwnPassword({required UpdateUserPassword request}) async {
    const path = '/user/password';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<void> updateOwnSalt({required UpdateUserSalt request}) async {
    const path = '/user/salt';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<Device> getCurrentDevice() async {
    const path = '/device';
    return await getOne(from: path, fromJson: Device.fromJson);
  }

  @override
  Future<Map<String, ServerState>> getCurrentDeviceConnections() async {
    const path = '/device/connections';
    return await underlying.get(Uri.parse('$server$path'), headers: authorization).andProcessResponseWith((r) {
      final json = jsonDecode(r.body) as Map<String, dynamic>;
      return json.map((k, v) => MapEntry(k, ServerState.fromJson(v)));
    });
  }

  @override
  Future<List<OperationProgress>> getOperations({required State? state}) async {
    final path = state == null ? '/operations' : '/operations?state=${state.name}';
    return await get(from: path, fromJson: OperationProgress.fromJson);
  }

  @override
  Future<OperationState> getOperationProgress({required String operation}) async {
    final path = '/operations/$operation/progress';
    return await getOne(from: path, fromJson: OperationState.fromJson);
  }

  @override
  Stream<OperationState> followOperation({required String operation}) {
    final path = '/operations/$operation/follow';
    return getSse(from: path, fromJson: OperationState.fromJson);
  }

  @override
  Future<void> stopOperation({required String operation}) async {
    final path = '/operations/$operation/stop';
    return await put(data: {}, to: path);
  }

  @override
  Future<void> resumeOperation({required String operation}) async {
    final path = '/operations/$operation/resume';
    return await put(data: {}, to: path);
  }

  @override
  Future<void> removeOperation({required String operation}) async {
    final path = '/operations/$operation';
    return await delete(from: path);
  }

  @override
  Future<OperationStarted> recoverUntil({
    required String definition,
    required DateTime until,
    required String? pathQuery,
    required String? destination,
    required bool? discardPaths,
  }) async {
    final keepStructure = discardPaths != null ? (!discardPaths).toString() : null;

    final params = {'query': pathQuery, 'destination': destination, 'keep_structure': keepStructure}
        .entries
        .where((e) => e.value != null)
        .map((e) => '${e.key}=${e.value}')
        .join('&');

    final path = ['/operations/recover/$definition/until/${until.toUtc().toIso8601String().split('.').first}Z', params]
        .where((e) => e.isNotEmpty)
        .join('?');

    return await putWithResponse(data: {}, to: path, fromJsonResponse: OperationStarted.fromJson);
  }

  @override
  Future<OperationStarted> recoverFrom({
    required String definition,
    required String entry,
    required String? pathQuery,
    required String? destination,
    required bool? discardPaths,
  }) async {
    final keepStructure = discardPaths != null ? (!discardPaths).toString() : null;

    final params = {'query': pathQuery, 'destination': destination, 'keep_structure': keepStructure}
        .entries
        .where((e) => e.value != null)
        .map((e) => '${e.key}=${e.value}')
        .join('&');

    final path = ['/operations/recover/$definition/from/$entry', params].where((e) => e.isNotEmpty).join('?');

    return await putWithResponse(data: {}, to: path, fromJsonResponse: OperationStarted.fromJson);
  }

  @override
  Future<OperationStarted> recoverFromLatest({
    required String definition,
    required String? pathQuery,
    required String? destination,
    required bool? discardPaths,
  }) async {
    final keepStructure = discardPaths != null ? (!discardPaths).toString() : null;

    final params = {'query': pathQuery, 'destination': destination, 'keep_structure': keepStructure}
        .entries
        .where((e) => e.value != null)
        .map((e) => '${e.key}=${e.value}')
        .join('&');

    final path = ['/operations/recover/$definition/latest', params].where((e) => e.isNotEmpty).join('?');

    return await putWithResponse(data: {}, to: path, fromJsonResponse: OperationStarted.fromJson);
  }

  @override
  Future<CreatedDatasetDefinition> defineBackup({required CreateDatasetDefinition request}) async {
    const path = '/datasets/definitions';
    return await post(data: request.toJson(), to: path, fromJsonResponse: CreatedDatasetDefinition.fromJson);
  }

  @override
  Future<void> updateBackup({required String definition, required UpdateDatasetDefinition request}) async {
    final path = '/datasets/definitions/$definition';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<OperationStarted> startBackup({required String definition}) async {
    final path = '/operations/backup/$definition';
    return await putWithResponse(data: {}, to: path, fromJsonResponse: OperationStarted.fromJson);
  }

  @override
  Future<Map<String, List<Rule>>> getBackupRules() async {
    const path = '/operations/backup/rules';
    return await underlying.get(Uri.parse('$server$path'), headers: authorization).andProcessResponseWith((r) {
      final json = jsonDecode(r.body) as Map<String, dynamic>;
      return json.map((k, rules) => MapEntry(k, (rules as List<dynamic>).map((rule) => Rule.fromJson(rule)).toList()));
    });
  }

  @override
  Future<SpecificationRules> getBackupSpecification({required String definition}) async {
    final path = '/operations/backup/rules/$definition/specification';
    return await getOne(from: path, fromJson: SpecificationRules.fromJson);
  }

  @override
  Future<List<Schedule>> getPublicSchedules() async {
    const path = '/schedules/public';
    return await get(from: path, fromJson: Schedule.fromJson);
  }

  @override
  Future<List<ActiveSchedule>> getConfiguredSchedules() async {
    const path = '/schedules/configured';
    return await get(from: path, fromJson: ActiveSchedule.fromJson);
  }

  @override
  Future<void> refreshConfiguredSchedules() async {
    const path = '/schedules/configured/refresh';
    return await put(data: {}, to: path);
  }

  @override
  Future<AnalyticsState> getAnalyticsState() async {
    const path = '/service/analytics';
    return await getOne(from: path, fromJson: AnalyticsState.fromJson);
  }

  @override
  Future<void> sendAnalyticsState() async {
    const path = '/service/analytics/send';
    return await put(data: {}, to: path);
  }
}
