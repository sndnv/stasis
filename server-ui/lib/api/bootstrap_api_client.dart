import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/model/devices/device_bootstrap_code.dart';

class BootstrapApiClient extends ApiClient implements DeviceBootstrapCodesApiClient {
  BootstrapApiClient({
    @override required String server,
    required http.Client underlying,
  }) : super(server: server, underlying: underlying);

  @override
  Future<List<DeviceBootstrapCode>> getBootstrapCodes({required bool privileged}) async {
    final path = privileged ? '/v1/devices/codes' : '/v1/devices/codes/own';
    return await get(from: path, fromJson: DeviceBootstrapCode.fromJson);
  }

  @override
  Future<void> deleteBootstrapCode({required bool privileged, required String forDevice}) async {
    final path = privileged ? '/v1/devices/codes/for-device/$forDevice' : '/v1/devices/codes/own/for-device/$forDevice';
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
