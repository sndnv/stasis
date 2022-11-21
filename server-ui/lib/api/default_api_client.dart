import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:server_ui/api/api_client.dart';
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
import 'package:server_ui/model/manifests/manifest.dart';
import 'package:server_ui/model/nodes/node.dart';
import 'package:server_ui/model/reservations/crate_storage_reservation.dart';
import 'package:server_ui/model/schedules/schedule.dart';
import 'package:server_ui/model/users/user.dart';

class DefaultApiClient extends ApiClient
    implements
        DatasetDefinitionsApiClient,
        DatasetEntriesApiClient,
        UsersApiClient,
        DevicesApiClient,
        SchedulesApiClient,
        NodesApiClient,
        ManifestsApiClient,
        ReservationsApiClient,
        ServiceApiClient {
  DefaultApiClient({
    required String server,
    required http.Client underlying,
  }) : super(server: server, underlying: underlying);

  @override
  Future<List<DatasetDefinition>> getDatasetDefinitions({required bool privileged}) async {
    final path = privileged ? '/datasets/definitions' : '/datasets/definitions/own';
    return await get(from: path, fromJson: DatasetDefinition.fromJson);
  }

  @override
  Future<CreatedDatasetDefinition> createDatasetDefinition({
    required bool privileged,
    required CreateDatasetDefinition request,
  }) async {
    final path = privileged ? '/datasets/definitions' : '/datasets/definitions/own';
    return await post(data: request.toJson(), to: path, fromJsonResponse: CreatedDatasetDefinition.fromJson);
  }

  @override
  Future<void> updateDatasetDefinition({
    required bool privileged,
    required String id,
    required UpdateDatasetDefinition request,
  }) async {
    final path = privileged ? '/datasets/definitions/$id' : '/datasets/definitions/own/$id';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<void> deleteDatasetDefinition({required bool privileged, required String id}) async {
    final path = privileged ? '/datasets/definitions/$id' : '/datasets/definitions/own/$id';
    return await delete(from: path);
  }

  @override
  Future<List<DatasetEntry>> getDatasetEntriesForDefinition({
    required bool privileged,
    required String definition,
  }) async {
    final path = privileged
        ? '/datasets/entries/for-definition/$definition'
        : '/datasets/entries/own/for-definition/$definition';
    return await get(from: path, fromJson: DatasetEntry.fromJson);
  }

  @override
  Future<void> deleteDatasetEntry({required bool privileged, required String id}) async {
    final path = privileged ? '/datasets/entries/$id' : '/datasets/entries/own/$id';
    return await delete(from: path);
  }

  @override
  Future<List<User>> getUsers() async {
    const path = '/users';
    return await get(from: path, fromJson: User.fromJson);
  }

  @override
  Future<CreatedUser> createUser({required CreateUser request}) async {
    const path = '/users';
    return await post(data: request.toJson(), to: path, fromJsonResponse: CreatedUser.fromJson);
  }

  @override
  Future<void> deleteUser({required String id}) async {
    final path = '/users/$id';
    return await delete(from: path);
  }

  @override
  Future<void> updateUserLimits({required String id, required UpdateUserLimits request}) async {
    final path = '/users/$id/limits';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<void> updateUserPermissions({required String id, required UpdateUserPermissions request}) async {
    final path = '/users/$id/permissions';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<void> updateUserState({required String id, required UpdateUserState request}) async {
    final path = '/users/$id/state';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<void> updateUserPassword({required String id, required UpdateUserPassword request}) async {
    final path = '/users/$id/password';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<User> getSelf() async {
    const path = '/users/self';
    return underlying.get(Uri.parse('$server$path')).andProcessResponseWith((r) => User.fromJson(jsonDecode(r.body)));
  }

  @override
  Future<void> deactivateSelf() async {
    const path = '/users/self/deactivate';
    return await put(data: {}, to: path);
  }

  @override
  Future<UpdatedUserSalt> resetOwnSalt() async {
    const path = '/users/self/salt';
    return underlying
        .put(Uri.parse('$server$path'))
        .andProcessResponseWith((r) => UpdatedUserSalt.fromJson(jsonDecode(r.body)));
  }

  @override
  Future<void> updateOwnPassword({required UpdateUserPassword request}) async {
    const path = '/users/self/password';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<List<Device>> getDevices({required bool privileged}) async {
    final path = privileged ? '/devices' : '/devices/own';
    return await get(from: path, fromJson: Device.fromJson);
  }

  @override
  Future<CreatedDevice> createDevice({required CreateDevicePrivileged request}) async {
    const path = '/devices';
    return await post(data: request.toJson(), to: path, fromJsonResponse: CreatedDevice.fromJson);
  }

  @override
  Future<CreatedDevice> createOwnDevice({required CreateDeviceOwn request}) async {
    const path = '/devices/own';
    return await post(data: request.toJson(), to: path, fromJsonResponse: CreatedDevice.fromJson);
  }

  @override
  Future<void> deleteDevice({required bool privileged, required String id}) async {
    final path = privileged ? '/devices/$id' : '/devices/own/$id';
    return await delete(from: path);
  }

  @override
  Future<void> updateDeviceLimits({
    required bool privileged,
    required String id,
    required UpdateDeviceLimits request,
  }) async {
    final path = privileged ? '/devices/$id/limits' : '/devices/own/$id/limits';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<void> updateDeviceState({
    required bool privileged,
    required String id,
    required UpdateDeviceState request,
  }) async {
    final path = privileged ? '/devices/$id/state' : '/devices/own/$id/state';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<List<Schedule>> getSchedules() async {
    const path = '/schedules';
    return await get(from: path, fromJson: Schedule.fromJson);
  }

  @override
  Future<List<Schedule>> getPublicSchedules() async {
    const path = '/schedules/public';
    return await get(from: path, fromJson: Schedule.fromJson);
  }

  @override
  Future<CreatedSchedule> createSchedule({required CreateSchedule request}) async {
    const path = '/schedules';
    return await post(data: request.toJson(), to: path, fromJsonResponse: CreatedSchedule.fromJson);
  }

  @override
  Future<void> updateSchedule({required String id, required UpdateSchedule request}) async {
    final path = '/schedules/$id';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<void> deleteSchedule({required String id}) async {
    final path = '/schedules/$id';
    return await delete(from: path);
  }

  @override
  Future<List<Node>> getNodes() async {
    const path = '/nodes';
    return await get(from: path, fromJson: Node.fromJson);
  }

  @override
  Future<CreatedNode> createNode({required CreateNode request}) async {
    const path = '/nodes';
    return await post(data: request.toJson(), to: path, fromJsonResponse: CreatedNode.fromJson);
  }

  @override
  Future<void> updateNode({required String id, required UpdateNode request}) async {
    final path = '/nodes/$id';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<void> deleteNode({required String id}) async {
    final path = '/nodes/$id';
    return await delete(from: path);
  }

  @override
  Future<Manifest> getManifest({required String crate}) async {
    final path = '/manifests/$crate';
    return underlying
        .get(Uri.parse('$server$path'))
        .andProcessResponseWith((r) => Manifest.fromJson(jsonDecode(r.body)));
  }

  @override
  Future<void> deleteManifest({required String crate}) async {
    final path = '/manifests/$crate';
    return await delete(from: path);
  }

  @override
  Future<List<CrateStorageReservation>> getCrateStorageReservations() async {
    const path = '/reservations';
    return await get(from: path, fromJson: CrateStorageReservation.fromJson);
  }

  @override
  Future<Ping> ping() async {
    const path = '/service/ping';
    return underlying.get(Uri.parse('$server$path')).andProcessResponseWith((r) => Ping.fromJson(jsonDecode(r.body)));
  }
}
