import 'dart:convert';

import 'package:stasis_client_ui/api/default_init_api.dart';
import 'package:stasis_client_ui/config/config.dart';
import 'package:stasis_client_ui/model/service/init_state.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import 'default_init_api_test.mocks.dart';

@GenerateMocks([http.Client])
void main() {
  const server = 'http://localhost:1234';

  group('A DefaultInitApi should', () {
    test('create new instances from config', () async {
      final config = Config(
        config: {
          'init': {
            'interface': 'abc',
            'port': 1234,
            'context': {
              'enabled': true,
              'keystore': {
                'path': './test/resources/localhost.p12',
                'password': '',
              },
            },
          },
        },
      );

      final client = DefaultInitApi.fromConfig(config: config);

      expect(client.server, 'https://abc:1234');
    });

    test('retrieve init state', () async {
      final underlying = MockClient();
      final client = DefaultInitApi(server: server, underlying: underlying);

      const state = InitState(startup: 'pending', cause: null, message: null);

      when(underlying.get(Uri.parse('$server/init'))).thenAnswer((_) async => http.Response(jsonEncode(state), 200));

      expect(await client.state(), state);
    });

    test('handle init state retrieval failures', () async {
      final underlying = MockClient();
      final client = DefaultInitApi(server: server, underlying: underlying);

      when(underlying.get(Uri.parse('$server/init'))).thenAnswer((_) async => throw Exception());

      expect(await client.state(), InitState.empty());
    });

    test('provide init credentials', () async {
      final underlying = MockClient();
      final client = DefaultInitApi(server: server, underlying: underlying);

      const username = 'user';
      const password = 'pass';

      when(underlying.post(Uri.parse('$server/init'), body: {'username': username, 'password': password}))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.provideCredentials(username: username, password: password), returnsNormally);
    });
  });
}
