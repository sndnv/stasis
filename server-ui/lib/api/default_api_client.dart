import 'dart:convert';

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
import 'package:server_ui/model/commands/command.dart';
import 'package:server_ui/model/datasets/dataset_definition.dart';
import 'package:server_ui/model/datasets/dataset_entry.dart';
import 'package:server_ui/model/devices/device.dart';
import 'package:server_ui/model/devices/device_key.dart';
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
    required super.server,
    required super.underlying,
  });

  @override
  Future<List<DatasetDefinition>> getDatasetDefinitions({required bool privileged}) async {
    final path = privileged ? '/v1/datasets/definitions' : '/v1/datasets/definitions/own';
    return await get(from: path, fromJson: DatasetDefinition.fromJson);
  }

  @override
  Future<CreatedDatasetDefinition> createDatasetDefinition({
    required bool privileged,
    required CreateDatasetDefinition request,
  }) async {
    final path = privileged ? '/v1/datasets/definitions' : '/v1/datasets/definitions/own';
    return await post(data: request.toJson(), to: path, fromJsonResponse: CreatedDatasetDefinition.fromJson);
  }

  @override
  Future<void> updateDatasetDefinition({
    required bool privileged,
    required String id,
    required UpdateDatasetDefinition request,
  }) async {
    final path = privileged ? '/v1/datasets/definitions/$id' : '/v1/datasets/definitions/own/$id';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<void> deleteDatasetDefinition({required bool privileged, required String id}) async {
    final path = privileged ? '/v1/datasets/definitions/$id' : '/v1/datasets/definitions/own/$id';
    return await delete(from: path);
  }

  @override
  Future<List<DatasetEntry>> getDatasetEntriesForDefinition({
    required bool privileged,
    required String definition,
  }) async {
    final path = privileged
        ? '/v1/datasets/entries/for-definition/$definition'
        : '/v1/datasets/entries/own/for-definition/$definition';
    return await get(from: path, fromJson: DatasetEntry.fromJson);
  }

  @override
  Future<void> deleteDatasetEntry({required bool privileged, required String id}) async {
    final path = privileged ? '/v1/datasets/entries/$id' : '/v1/datasets/entries/own/$id';
    return await delete(from: path);
  }

  @override
  Future<List<User>> getUsers() async {
    const path = '/v1/users';
    return await get(from: path, fromJson: User.fromJson);
  }

  @override
  Future<CreatedUser> createUser({required CreateUser request}) async {
    const path = '/v1/users';
    return await post(data: request.toJson(), to: path, fromJsonResponse: CreatedUser.fromJson);
  }

  @override
  Future<void> deleteUser({required String id}) async {
    final path = '/v1/users/$id';
    return await delete(from: path);
  }

  @override
  Future<void> updateUserLimits({required String id, required UpdateUserLimits request}) async {
    final path = '/v1/users/$id/limits';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<void> updateUserPermissions({required String id, required UpdateUserPermissions request}) async {
    final path = '/v1/users/$id/permissions';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<void> updateUserState({required String id, required UpdateUserState request}) async {
    final path = '/v1/users/$id/state';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<void> updateUserPassword({required String id, required UpdateUserPassword request}) async {
    final path = '/v1/users/$id/password';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<User> getSelf() async {
    const path = '/v1/users/self';
    return underlying.get(Uri.parse('$server$path')).andProcessResponseWith((r) => User.fromJson(jsonDecode(r.body)));
  }

  @override
  Future<void> deactivateSelf() async {
    const path = '/v1/users/self/deactivate';
    return await put(data: {}, to: path);
  }

  @override
  Future<UpdatedUserSalt> resetOwnSalt() async {
    const path = '/v1/users/self/salt';
    return underlying
        .put(Uri.parse('$server$path'))
        .andProcessResponseWith((r) => UpdatedUserSalt.fromJson(jsonDecode(r.body)));
  }

  @override
  Future<void> updateOwnPassword({required UpdateUserPassword request}) async {
    const path = '/v1/users/self/password';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<List<Device>> getDevices({required bool privileged}) async {
    final path = privileged ? '/v1/devices' : '/v1/devices/own';
    return await get(from: path, fromJson: Device.fromJson);
  }

  @override
  Future<CreatedDevice> createDevice({required CreateDevicePrivileged request}) async {
    const path = '/v1/devices';
    return await post(data: request.toJson(), to: path, fromJsonResponse: CreatedDevice.fromJson);
  }

  @override
  Future<CreatedDevice> createOwnDevice({required CreateDeviceOwn request}) async {
    const path = '/v1/devices/own';
    return await post(data: request.toJson(), to: path, fromJsonResponse: CreatedDevice.fromJson);
  }

  @override
  Future<void> deleteDevice({required bool privileged, required String id}) async {
    final path = privileged ? '/v1/devices/$id' : '/v1/devices/own/$id';
    return await delete(from: path);
  }

  @override
  Future<void> updateDeviceLimits({
    required bool privileged,
    required String id,
    required UpdateDeviceLimits request,
  }) async {
    final path = privileged ? '/v1/devices/$id/limits' : '/v1/devices/own/$id/limits';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<void> updateDeviceState({
    required bool privileged,
    required String id,
    required UpdateDeviceState request,
  }) async {
    final path = privileged ? '/v1/devices/$id/state' : '/v1/devices/own/$id/state';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<List<DeviceKey>> getDeviceKeys({required bool privileged}) async {
    final path = privileged ? '/v1/devices/keys' : '/v1/devices/own/keys';
    return await get(from: path, fromJson: DeviceKey.fromJson);
  }

  @override
  Future<DeviceKey?> getDeviceKey({required bool privileged, required String forDevice}) async {
    final path = privileged ? '/v1/devices/$forDevice/key' : '/v1/devices/own/$forDevice/key';
    return underlying
        .get(Uri.parse('$server$path'))
        .andProcessResponseWith((r) => DeviceKey.fromJson(jsonDecode(r.body)));
  }

  @override
  Future<void> deleteDeviceKey({required bool privileged, required String forDevice}) async {
    final path = privileged ? '/v1/devices/$forDevice/key' : '/v1/devices/own/$forDevice/key';
    return await delete(from: path);
  }

  @override
  Future<List<Command>> getCommands() async {
    const path = '/v1/devices/commands';
    return await get(from: path, fromJson: Command.fromJson);
  }

  @override
  Future<void> createCommand({required CommandParameters request}) async {
    const path = '/v1/devices/commands';
    return await underlying
        .post(
          Uri.parse('$server$path'),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode(request.toJson()),
        )
        .andProcessResponse();
  }

  @override
  Future<void> deleteCommand({required int sequenceId}) async {
    final path = '/v1/devices/commands/$sequenceId';
    return await delete(from: path);
  }

  @override
  Future<void> truncateCommands({required DateTime olderThan}) async {
    final path = '/v1/devices/commands/truncate?older_than=${olderThan.toUtc().toIso8601String().split('.').first}Z';
    return await underlying.put(Uri.parse('$server$path')).andProcessResponse();
  }

  @override
  Future<List<Command>> getDeviceCommands({required bool privileged, required String forDevice}) async {
    final path = privileged ? '/v1/devices/$forDevice/commands' : '/v1/devices/own/$forDevice/commands';
    return await get(from: path, fromJson: Command.fromJson);
  }

  @override
  Future<void> createDeviceCommand({
    required bool privileged,
    required CommandParameters request,
    required String forDevice,
  }) async {
    final path = privileged ? '/v1/devices/$forDevice/commands' : '/v1/devices/own/$forDevice/commands';
    return await underlying
        .post(
          Uri.parse('$server$path'),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode(request.toJson()),
        )
        .andProcessResponse();
  }

  @override
  Future<List<Schedule>> getSchedules() async {
    const path = '/v1/schedules';
    return await get(from: path, fromJson: Schedule.fromJson);
  }

  @override
  Future<List<Schedule>> getPublicSchedules() async {
    const path = '/v1/schedules/public';
    return await get(from: path, fromJson: Schedule.fromJson);
  }

  @override
  Future<CreatedSchedule> createSchedule({required CreateSchedule request}) async {
    const path = '/v1/schedules';
    return await post(data: request.toJson(), to: path, fromJsonResponse: CreatedSchedule.fromJson);
  }

  @override
  Future<void> updateSchedule({required String id, required UpdateSchedule request}) async {
    final path = '/v1/schedules/$id';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<void> deleteSchedule({required String id}) async {
    final path = '/v1/schedules/$id';
    return await delete(from: path);
  }

  @override
  Future<List<Node>> getNodes() async {
    const path = '/v1/nodes';
    return await get(from: path, fromJson: Node.fromJson);
  }

  @override
  Future<CreatedNode> createNode({required CreateNode request}) async {
    const path = '/v1/nodes';
    return await post(data: request.toJson(), to: path, fromJsonResponse: CreatedNode.fromJson);
  }

  @override
  Future<void> updateNode({required String id, required UpdateNode request}) async {
    final path = '/v1/nodes/$id';
    return await put(data: request.toJson(), to: path);
  }

  @override
  Future<void> deleteNode({required String id}) async {
    final path = '/v1/nodes/$id';
    return await delete(from: path);
  }

  @override
  Future<Manifest> getManifest({required String crate}) async {
    final path = '/v1/manifests/$crate';
    return underlying
        .get(Uri.parse('$server$path'))
        .andProcessResponseWith((r) => Manifest.fromJson(jsonDecode(r.body)));
  }

  @override
  Future<void> deleteManifest({required String crate}) async {
    final path = '/v1/manifests/$crate';
    return await delete(from: path);
  }

  @override
  Future<List<CrateStorageReservation>> getCrateStorageReservations() async {
    const path = '/v1/reservations';
    return await get(from: path, fromJson: CrateStorageReservation.fromJson);
  }

  @override
  Future<Ping> ping() async {
    const path = '/v1/service/ping';
    return underlying.get(Uri.parse('$server$path')).andProcessResponseWith((r) => Ping.fromJson(jsonDecode(r.body)));
  }
}
