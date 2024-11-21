import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
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

abstract class ApiClient {
  ApiClient({required this.server, required this.underlying, String? token}) : _token = token;

  final String server;
  final http.Client underlying;
  final String? _token;

  late Map<String, String>? authorization = _token != null ? {'Authorization': 'Bearer $_token'} : null;
  late final Map<String, String> contentType = {'Content-Type': 'application/json'};

  Future<List<T>> get<T>({
    required String from,
    required T Function(Map<String, dynamic> json) fromJson,
  }) async {
    return underlying.get(Uri.parse('$server$from'), headers: authorization).andProcessResponseWith(
        (r) => (jsonDecode(r.body) as Iterable<dynamic>).map((json) => fromJson(json)).toList());
  }

  Future<T> getOne<T>({
    required String from,
    required T Function(Map<String, dynamic> json) fromJson,
  }) async {
    return underlying
        .get(Uri.parse('$server$from'), headers: authorization)
        .andProcessResponseWith((r) => fromJson(jsonDecode(r.body)));
  }

  Future<T?> getOptional<T>({
    required String from,
    required T Function(Map<String, dynamic> json) fromJson,
  }) async {
    return underlying
        .get(Uri.parse('$server$from'), headers: authorization)
        .andProcessOptionalResponseWith((r) => fromJson(jsonDecode(r.body)));
  }

  Stream<T> getSse<T>({
    required String from,
    required T Function(Map<String, dynamic> json) fromJson,
  }) {
    final dataExtractor = RegExp(r'^data:(.*)$');
    final controller = StreamController<T>();

    final request = http.Request('GET', Uri.parse('$server$from'));
    request.headers.addAll(authorization ?? {});

    underlying.send(request).andProcessResponseWith((response) {
      final subscription = response.stream.transform(utf8.decoder).transform(const LineSplitter()).listen((line) {
        final data = dataExtractor.firstMatch(line.trim())?.group(1)?.trim();
        if (data != null && data.isNotEmpty) {
          controller.add(fromJson(jsonDecode(data)));
        } else {
          // end of event, non-data line or invalid data - skip
        }
      });

      subscription.onError((e, s) => controller.addError(e, s));
      subscription.onDone(() => controller.close());
    }).onError<Exception>((e, s) {
      controller.addError(e, s);
      controller.close();
    });

    return controller.stream;
  }

  Future<T> post<T>({
    required Map<String, dynamic> data,
    required String to,
    required T Function(Map<String, dynamic> json) fromJsonResponse,
  }) async {
    return await underlying
        .post(
          Uri.parse('$server$to'),
          headers: {...contentType, ...(authorization ?? {})},
          body: jsonEncode(data),
        )
        .andProcessResponseWith((r) => fromJsonResponse(jsonDecode(r.body)));
  }

  Future<void> put({required Map<String, dynamic> data, required String to}) async {
    if (data.isEmpty) {
      await underlying.put(Uri.parse('$server$to'), headers: authorization).andProcessResponse();
    } else {
      await underlying
          .put(
            Uri.parse('$server$to'),
            headers: {...contentType, ...(authorization ?? {})},
            body: jsonEncode(data),
          )
          .andProcessResponse();
    }
  }

  Future<T> putWithResponse<T>({
    required Map<String, dynamic> data,
    required String to,
    required T Function(Map<String, dynamic> json) fromJsonResponse,
  }) async {
    if (data.isEmpty) {
      return await underlying
          .put(Uri.parse('$server$to'), headers: authorization)
          .andProcessResponseWith((r) => fromJsonResponse(jsonDecode(r.body)));
    } else {
      return await underlying
          .put(
            Uri.parse('$server$to'),
            headers: {...contentType, ...(authorization ?? {})},
            body: jsonEncode(data),
          )
          .andProcessResponseWith((r) => fromJsonResponse(jsonDecode(r.body)));
    }
  }

  Future<void> delete({required String from}) async {
    final _ = await underlying.delete(Uri.parse('$server$from'), headers: authorization).andProcessResponse();
  }
}

abstract class InitApi {
  Future<InitState> state();

  Future<void> provideCredentials({required String username, required String password});
}

abstract class ClientApi {
  Future<bool> isActive();

  Future<void> stop();

  Future<List<DatasetDefinition>> getDatasetDefinitions();

  Future<void> deleteDatasetDefinition({required String definition});

  Future<List<DatasetEntry>> getDatasetEntries();

  Future<List<DatasetEntry>> getDatasetEntriesForDefinition({required String definition});

  Future<DatasetEntry?> getLatestDatasetEntryForDefinition({required String definition});

  Future<void> deleteDatasetEntry({required String entry});

  Future<DatasetMetadata> getDatasetMetadata({required String entry});

  Future<DatasetMetadataSearchResult> searchDatasetMetadata({required String searchQuery, required DateTime? until});

  Future<User> getSelf();

  Future<void> updateOwnPassword({required UpdateUserPassword request});

  Future<void> updateOwnSalt({required UpdateUserSalt request});

  Future<Device> getCurrentDevice();

  Future<Map<String, ServerState>> getCurrentDeviceConnections();

  Future<List<OperationProgress>> getOperations({required State? state});

  Future<OperationState> getOperationProgress({required String operation});

  Stream<OperationState> followOperation({required String operation});

  Future<void> stopOperation({required String operation});

  Future<void> resumeOperation({required String operation});

  Future<void> removeOperation({required String operation});

  Future<Map<String, List<Rule>>> getBackupRules();

  Future<SpecificationRules> getBackupSpecification({required String definition});

  Future<OperationStarted> startBackup({required String definition});

  Future<CreatedDatasetDefinition> defineBackup({required CreateDatasetDefinition request});

  Future<void> updateBackup({required String definition, required UpdateDatasetDefinition request});

  Future<OperationStarted> recoverUntil({
    required String definition,
    required DateTime until,
    required String? pathQuery,
    required String? destination,
    required bool? discardPaths,
  });

  Future<OperationStarted> recoverFrom({
    required String definition,
    required String entry,
    required String? pathQuery,
    required String? destination,
    required bool? discardPaths,
  });

  Future<OperationStarted> recoverFromLatest({
    required String definition,
    required String? pathQuery,
    required String? destination,
    required bool? discardPaths,
  });

  Future<List<Schedule>> getPublicSchedules();

  Future<List<ActiveSchedule>> getConfiguredSchedules();

  Future<void> refreshConfiguredSchedules();
}

class AuthenticationFailure implements Exception {
  final String message = 'Failed to authenticate user';

  @override
  String toString() {
    return message;
  }
}

class AuthorizationFailure implements Exception {
  final String message = 'User not allowed to access resource';

  @override
  String toString() {
    return message;
  }
}

class ConflictFailure implements Exception {
  final String message = 'Conflict encountered while processing request';

  @override
  String toString() {
    return message;
  }
}

class BadRequest implements Exception {
  BadRequest({required this.message, required this.status});

  final String message;
  final int status;

  @override
  String toString() {
    return message.trim().isEmpty ? '($status) Bad Request' : '($status) Bad Request - $message';
  }
}

class InternalServerError implements Exception {
  InternalServerError({required this.message, required this.status});

  final String message;
  final int status;

  @override
  String toString() {
    return message.trim().isEmpty ? '($status) Internal Server Error' : '($status) Internal Server Error - $message';
  }
}

class ApiUnavailable implements Exception {
  final String message = 'Failed to reach client API';

  @override
  String toString() {
    return message;
  }
}

extension ExtendedResponse<R extends http.BaseResponse> on Future<R> {
  Future<T> andProcessResponseWith<T>(T Function(R response) f) async {
    try {
      final response = await this;

      if (response.statusCode == 401) {
        return Future.error(AuthenticationFailure());
      } else if (response.statusCode == 403) {
        return Future.error(AuthorizationFailure());
      } else if (response.statusCode == 409) {
        return Future.error(ConflictFailure());
      } else if (response.statusCode >= 400 && response.statusCode < 500) {
        return Future.error(
          BadRequest(
            message: (response is http.Response ? response.body : ''),
            status: response.statusCode,
          ),
        );
      } else if (response.statusCode >= 500) {
        return Future.error(
          InternalServerError(
            message: (response is http.Response ? response.body : ''),
            status: response.statusCode,
          ),
        );
      } else {
        return f(response);
      }
    } on http.ClientException catch (_) {
      return Future.error(ApiUnavailable());
    }
  }

  Future<T?> andProcessOptionalResponseWith<T>(T Function(R response) f) async {
    try {
      return await this.andProcessResponseWith(f);
    } on BadRequest catch (e) {
      if (e.status == 404) {
        return null;
      } else {
        return Future.error(e);
      }
    }
  }

  Future<void> andProcessResponse() async {
    return andProcessResponseWith((response) => response);
  }
}
