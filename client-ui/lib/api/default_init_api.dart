import 'dart:io';

import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/api/endpoint_context.dart';
import 'package:stasis_client_ui/config/config.dart';
import 'package:stasis_client_ui/model/service/init_state.dart';
import 'package:http/io_client.dart' as io_client;

class DefaultInitApi extends ApiClient implements InitApi {
  DefaultInitApi({
    required super.server,
    required super.underlying,
  });

  static DefaultInitApi fromConfig({required Config config, bool insecure = false}) {
    final apiConfig = config.getConfig('init');

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
        },
    );

    return DefaultInitApi(server: apiUrl, underlying: underlying);
  }

  @override
  Future<InitState> state() async {
    const path = '/init';
    return await getOne(from: path, fromJson: InitState.fromJson).onError((_, __) => InitState.empty());
  }

  @override
  Future<void> provideCredentials({required String username, required String password}) async {
    const path = '/init';

    return await underlying.post(
      Uri.parse('$server$path'),
      body: {'username': username, 'password': password},
    ).andProcessResponse();
  }
}
