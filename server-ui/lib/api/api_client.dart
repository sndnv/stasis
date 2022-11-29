import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:oauth2/oauth2.dart' as oauth2;
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
import 'package:server_ui/model/devices/device_bootstrap_code.dart';
import 'package:server_ui/model/manifests/manifest.dart';
import 'package:server_ui/model/nodes/node.dart';
import 'package:server_ui/model/reservations/crate_storage_reservation.dart';
import 'package:server_ui/model/schedules/schedule.dart';
import 'package:server_ui/model/users/user.dart';

abstract class ApiClient {
  ApiClient({required this.server, required this.underlying});

  String server;
  http.Client underlying;

  Future<List<T>> get<T>({
    required String from,
    required T Function(Map<String, dynamic> json) fromJson,
  }) async {
    return underlying.get(Uri.parse('$server$from')).andProcessResponseWith(
        (r) => (jsonDecode(r.body) as Iterable<dynamic>).map((json) => fromJson(json)).toList());
  }

  Future<T> post<T>({
    required Map<String, dynamic> data,
    required String to,
    required T Function(Map<String, dynamic> json) fromJsonResponse,
  }) async {
    return await underlying
        .post(
          Uri.parse('$server$to'),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode(data),
        )
        .andProcessResponseWith((r) => fromJsonResponse(jsonDecode(r.body)));
  }

  Future<void> put({required Map<String, dynamic> data, required String to}) async {
    final _ = await underlying
        .put(
          Uri.parse('$server$to'),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode(data),
        )
        .andProcessResponse();
  }

  Future<void> delete({required String from}) async {
    final _ = await underlying.delete(Uri.parse('$server$from')).andProcessResponse();
  }
}

abstract class DatasetDefinitionsApiClient {
  Future<List<DatasetDefinition>> getDatasetDefinitions({required bool privileged});

  Future<CreatedDatasetDefinition> createDatasetDefinition({
    required bool privileged,
    required CreateDatasetDefinition request,
  });

  Future<void> updateDatasetDefinition({
    required bool privileged,
    required String id,
    required UpdateDatasetDefinition request,
  });

  Future<void> deleteDatasetDefinition({required bool privileged, required String id});
}

abstract class DatasetEntriesApiClient {
  Future<List<DatasetEntry>> getDatasetEntriesForDefinition({required bool privileged, required String definition});

  Future<void> deleteDatasetEntry({required bool privileged, required String id});
}

abstract class UsersApiClient {
  Future<List<User>> getUsers();

  Future<CreatedUser> createUser({required CreateUser request});

  Future<void> deleteUser({required String id});

  Future<void> updateUserLimits({required String id, required UpdateUserLimits request});

  Future<void> updateUserPermissions({required String id, required UpdateUserPermissions request});

  Future<void> updateUserState({required String id, required UpdateUserState request});

  Future<void> updateUserPassword({required String id, required UpdateUserPassword request});

  Future<User> getSelf();

  Future<void> deactivateSelf();

  Future<UpdatedUserSalt> resetOwnSalt();

  Future<void> updateOwnPassword({required UpdateUserPassword request});
}

abstract class DevicesApiClient {
  Future<List<Device>> getDevices({required bool privileged});

  Future<CreatedDevice> createDevice({required CreateDevicePrivileged request});

  Future<CreatedDevice> createOwnDevice({required CreateDeviceOwn request});

  Future<void> deleteDevice({required bool privileged, required String id});

  Future<void> updateDeviceLimits({required bool privileged, required String id, required UpdateDeviceLimits request});

  Future<void> updateDeviceState({required bool privileged, required String id, required UpdateDeviceState request});
}

abstract class SchedulesApiClient {
  Future<List<Schedule>> getSchedules();

  Future<List<Schedule>> getPublicSchedules();

  Future<CreatedSchedule> createSchedule({required CreateSchedule request});

  Future<void> updateSchedule({required String id, required UpdateSchedule request});

  Future<void> deleteSchedule({required String id});
}

abstract class NodesApiClient {
  Future<List<Node>> getNodes();

  Future<CreatedNode> createNode({required CreateNode request});

  Future<void> updateNode({required String id, required UpdateNode request});

  Future<void> deleteNode({required String id});
}

abstract class ManifestsApiClient {
  Future<Manifest> getManifest({required String crate});

  Future<void> deleteManifest({required String crate});
}

abstract class ReservationsApiClient {
  Future<List<CrateStorageReservation>> getCrateStorageReservations();
}

abstract class DeviceBootstrapCodesApiClient {
  abstract String server;

  Future<List<DeviceBootstrapCode>> getBootstrapCodes({required bool privileged});

  Future<void> deleteBootstrapCode({required bool privileged, required String forDevice});

  Future<DeviceBootstrapCode> generateBootstrapCode({required String forDevice});
}

abstract class ServiceApiClient {
  Future<Ping> ping();
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

class BadRequest implements Exception {
  BadRequest({required this.message});

  final String message;

  @override
  String toString() {
    return 'Bad Request - $message';
  }
}

class InternalServerError implements Exception {
  InternalServerError({required this.message});

  final String message;

  @override
  String toString() {
    return 'Internal Server Error - $message';
  }
}

extension ExtendedResponse on Future<http.Response> {
  Future<T> andProcessResponseWith<T>(T Function(http.Response response) f) async {
    try {
      final response = await this;

      if (response.statusCode == 401) {
        return Future.error(AuthenticationFailure());
      } else if (response.statusCode == 403) {
        return Future.error(AuthorizationFailure());
      } else if (response.statusCode >= 400 && response.statusCode < 500) {
        return Future.error(BadRequest(message: response.body));
      } else if (response.statusCode >= 500) {
        return Future.error(InternalServerError(message: response.body));
      } else {
        return f(response);
      }
    } on oauth2.AuthorizationException catch (_) {
      return Future.error(AuthenticationFailure());
    } on oauth2.ExpirationException catch (_) {
      return Future.error(AuthenticationFailure());
    }
  }

  Future<void> andProcessResponse() async {
    return andProcessResponseWith((response) => response);
  }
}
