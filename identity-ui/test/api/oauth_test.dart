import 'package:flutter_test/flutter_test.dart';
import 'package:identity_ui/api/oauth.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  final config = OAuthConfig(
    authorizationEndpoint: Uri.parse('http://localhost:1234/authorize'),
    tokenEndpoint: Uri.parse('http://localhost:1234/token'),
    clientId: 'test-client',
    redirectUri: Uri.parse('http://localhost:1234/callback'),
    scopes: ['a', 'b', 'c'],
  );

  group('OAuth should', () {
    test('generate authorization URLs', () async {
      SharedPreferences.setMockInitialValues({});

      final generated = await OAuth.generateAuthorizationUri(config).then((value) => value.toString());

      expect(generated, contains(config.authorizationEndpoint.toString()));
      expect(generated, contains('response_type=code'));
      expect(generated, contains('client_id=${config.clientId}'));
      expect(generated, contains('redirect_uri=${Uri.encodeQueryComponent(config.redirectUri.toString())}'));
      expect(generated, contains('code_challenge='));
      expect(generated, contains('code_challenge_method=S256'));
      expect(generated, contains('state='));
      expect(generated, contains('scope=${config.scopes.join('+')}'));
    });
  });
}
