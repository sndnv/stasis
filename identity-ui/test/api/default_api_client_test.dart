import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:identity_ui/api/api_client.dart';
import 'package:identity_ui/api/default_api_client.dart';
import 'package:identity_ui/model/api.dart';
import 'package:identity_ui/model/client.dart';
import 'package:identity_ui/model/requests/create_api.dart';
import 'package:identity_ui/model/requests/create_client.dart';
import 'package:identity_ui/model/requests/create_owner.dart';
import 'package:identity_ui/model/requests/update_client.dart';
import 'package:identity_ui/model/requests/update_client_credentials.dart';
import 'package:identity_ui/model/requests/update_owner.dart';
import 'package:identity_ui/model/requests/update_owner_credentials.dart';
import 'package:identity_ui/model/resource_owner.dart';
import 'package:identity_ui/model/stored_authorization_code.dart';
import 'package:identity_ui/model/stored_refresh_token.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:oauth2/oauth2.dart' as oauth2;

import 'default_api_client_test.mocks.dart';

@GenerateMocks([http.Client])
void main() {
  const server = 'http://localhost:1234';
  const applicationJson = {'Content-Type': 'application/json'};

  group('A DefaultApiClient should', () {
    test('get APIs', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const response = '[{"id":"api-1"},{"id":"api-2"},{"id":"3"}]';

      when(underlying.get(Uri.parse('$server/manage/apis'))).thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getApis(), const [Api(id: 'api-1'), Api(id: 'api-2'), Api(id: '3')]);
    });

    test('post APIs', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const request = CreateApi(id: 'new-api');
      const jsonRequest = '{"id":"new-api"}';

      when(underlying.post(Uri.parse('$server/manage/apis'), headers: applicationJson, body: jsonRequest))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.postApi(request), returnsNormally);
    });

    test('delete APIs', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      when(underlying.delete(Uri.parse('$server/manage/apis/api-id'))).thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteApi('api-id'), returnsNormally);
    });

    test('get clients', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const response = '[{"id":"id-1","redirect_uri":"uri-1","token_expiration":1,"active":true,"subject":"sub-1"}]';

      when(underlying.get(Uri.parse('$server/manage/clients'))).thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getClients(),
          const [Client(id: 'id-1', redirectUri: 'uri-1', tokenExpiration: 1, active: true, subject: 'sub-1')]);
    });

    test('post clients', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const request = CreateClient(redirectUri: 'uri-1', tokenExpiration: 1, rawSecret: 'secret-1', subject: 'sub-1');
      const jsonRequest = '{"redirect_uri":"uri-1","token_expiration":1,"raw_secret":"secret-1","subject":"sub-1"}';

      when(underlying.post(Uri.parse('$server/manage/clients'), headers: applicationJson, body: jsonRequest))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.postClient(request), returnsNormally);
    });

    test('put clients', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const request = UpdateClient(tokenExpiration: 1, active: false);
      const jsonRequest = '{"token_expiration":1,"active":false}';

      when(underlying.put(Uri.parse('$server/manage/clients/client-id'), headers: applicationJson, body: jsonRequest))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.putClient('client-id', request), returnsNormally);
    });

    test('put client credentials', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const request = UpdateClientCredentials(rawSecret: 'secret-1');
      const jsonRequest = '{"raw_secret":"secret-1"}';

      when(underlying.put(Uri.parse('$server/manage/clients/client-id/credentials'),
              headers: applicationJson, body: jsonRequest))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.putClientCredentials('client-id', request), returnsNormally);
    });

    test('delete clients', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      when(underlying.delete(Uri.parse('$server/manage/clients/client-id')))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteClient('client-id'), returnsNormally);
    });

    test('get owners', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const response = '[{"username":"user-1","allowed_scopes":["scope-1","scope-2"],"active":true,"subject":"sub-1"}]';

      when(underlying.get(Uri.parse('$server/manage/owners'))).thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getOwners(), const [
        ResourceOwner(username: 'user-1', allowedScopes: ['scope-1', 'scope-2'], active: true, subject: 'sub-1')
      ]);
    });

    test('post owners', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const request =
          CreateOwner(username: 'user-1', rawPassword: 'password-1', allowedScopes: ['scope-1'], subject: 'sub-1');
      const jsonRequest =
          '{"username":"user-1","raw_password":"password-1","allowed_scopes":["scope-1"],"subject":"sub-1"}';

      when(underlying.post(Uri.parse('$server/manage/owners'), headers: applicationJson, body: jsonRequest))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.postOwner(request), returnsNormally);
    });

    test('put owners', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const request = UpdateOwner(allowedScopes: ['scope-1'], active: false);
      const jsonRequest = '{"allowed_scopes":["scope-1"],"active":false}';

      when(underlying.put(Uri.parse('$server/manage/owners/owner-id'), headers: applicationJson, body: jsonRequest))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.putOwner('owner-id', request), returnsNormally);
    });

    test('put owner credentials', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const request = UpdateOwnerCredentials(rawPassword: 'password-1');
      const jsonRequest = '{"raw_password":"password-1"}';

      when(underlying.put(Uri.parse('$server/manage/owners/owner-id/credentials'),
              headers: applicationJson, body: jsonRequest))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.putOwnerCredentials('owner-id', request), returnsNormally);
    });

    test('delete owners', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      when(underlying.delete(Uri.parse('$server/manage/owners/owner-id')))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteOwner('owner-id'), returnsNormally);
    });

    test('get codes', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const response = '[{"code":"code-1","client":"client-1","owner":"owner-1"}]';

      when(underlying.get(Uri.parse('$server/manage/codes'))).thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getCodes(),
          const [StoredAuthorizationCode(code: 'code-1', client: 'client-1', owner: 'owner-1')]);
    });

    test('delete codes', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      when(underlying.delete(Uri.parse('$server/manage/codes/code-id')))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteCode('code-id'), returnsNormally);
    });

    test('get tokens', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      const response = '[{"token":"token-1","client":"client-1","owner":"owner-1","scope":"scope-1","expiration":"1"}]';

      when(underlying.get(Uri.parse('$server/manage/tokens'))).thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getTokens(), const [
        StoredRefreshToken(token: 'token-1', client: 'client-1', owner: 'owner-1', scope: 'scope-1', expiration: '1')
      ]);
    });

    test('delete tokens', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      when(underlying.delete(Uri.parse('$server/manage/tokens/token-id')))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteToken('token-id'), returnsNormally);
    });

    test('support pinging the server', () async {
      final underlying = MockClient();
      final client = DefaultApiClient(server: server, underlying: underlying);

      when(underlying.get(Uri.parse('$server/service/health'))).thenAnswer((_) async => http.Response('', 200));

      expect(await client.ping(), true);
    });

    test('handle valid responses', () async {
      final future100 = Future.value(http.Response('', 100));
      final future200 = Future.value(http.Response('', 200));
      final future300 = Future.value(http.Response('', 300));

      expect(await future100.andProcessResponseWith((response) => response.statusCode), 100);
      expect(await future200.andProcessResponseWith((response) => response.statusCode), 200);
      expect(await future300.andProcessResponseWith((response) => response.statusCode), 300);
    });

    test('handle 401 responses', () async {
      final future = Future.value(http.Response('', 401));
      expect(() async => await future.andProcessResponse(), throwsA(const TypeMatcher<AuthenticationFailure>()));
    });

    test('handle 403 responses', () async {
      final future = Future.value(http.Response('', 403));
      expect(() async => await future.andProcessResponse(), throwsA(const TypeMatcher<AuthorizationFailure>()));
    });

    test('handle 4xx responses', () async {
      final future = Future.value(http.Response('', 400));
      expect(() async => await future.andProcessResponse(), throwsA(const TypeMatcher<BadRequest>()));
    });

    test('handle 5xx responses', () async {
      final future = Future.value(http.Response('', 500));
      expect(() async => await future.andProcessResponse(), throwsA(const TypeMatcher<InternalServerError>()));
    });

    test('handle authorization and expiration failures', () async {
      final Future<http.Response> authorizationFailureFuture =
          Future.error(oauth2.AuthorizationException('error', 'description', null));

      final Future<http.Response> expirationFailureFuture =
          Future.error(oauth2.ExpirationException(oauth2.Credentials('token')));

      expect(() async => await authorizationFailureFuture.andProcessResponse(),
          throwsA(const TypeMatcher<AuthenticationFailure>()));

      expect(() async => await expirationFailureFuture.andProcessResponse(),
          throwsA(const TypeMatcher<AuthenticationFailure>()));
    });
  });
}
