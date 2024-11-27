import 'dart:convert';

import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/model/devices/device_bootstrap_code.dart';

class BootstrapApiClient extends ApiClient implements DeviceBootstrapCodesApiClient {
  BootstrapApiClient({
    @override required super.server,
    required super.underlying,
  });

  @override
  Future<List<DeviceBootstrapCode>> getBootstrapCodes({required bool privileged}) async {
    final path = privileged ? '/v1/devices/codes' : '/v1/devices/codes/own';
    return await get(from: path, fromJson: DeviceBootstrapCode.fromJson);
  }

  @override
  Future<void> deleteBootstrapCode({required bool privileged, required String code}) async {
    final path = privileged ? '/v1/devices/codes/$code' : '/v1/devices/codes/own/$code';
    return await delete(from: path);
  }

  @override
  Future<DeviceBootstrapCode> generateBootstrapCode({required String forDevice}) async {
    final path = '/v1/devices/codes/own/for-device/$forDevice';
    return underlying
        .put(Uri.parse('$server$path'))
        .andProcessResponseWith((r) => DeviceBootstrapCode.fromJson(jsonDecode(r.body)));
  }
}
