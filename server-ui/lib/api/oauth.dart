import 'dart:math';

import 'package:http/http.dart';
import 'package:oauth2/oauth2.dart' as oauth2;
import 'package:shared_preferences/shared_preferences.dart';

class OAuth {
  static Future<Uri> generateAuthorizationUri(OAuthConfig config) async {
    final verifier = _generateRandomString(Defaults.codeVerifierSize);
    final state = _generateRandomString(Defaults.stateSize);

    final grant = oauth2.AuthorizationCodeGrant(config.clientId, config.authorizationEndpoint, config.tokenEndpoint,
        codeVerifier: verifier);

    final prefs = await SharedPreferences.getInstance();
    prefs.setString(Keys._codeVerifier, verifier);
    prefs.setString(Keys._state, state);

    return grant.getAuthorizationUrl(config.redirectUri, scopes: config.scopes, state: state);
  }

  static Future<Client> handleAuthorizationResponse(
    OAuthConfig config,
    Map<String, String> queryParams, {
    Client? underlying,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    final verifier = prefs.getString(Keys._codeVerifier);
    final state = prefs.getString(Keys._state);

    if (verifier != null) {
      final grant = oauth2.AuthorizationCodeGrant(
        config.clientId,
        config.authorizationEndpoint,
        config.tokenEndpoint,
        codeVerifier: verifier,
        httpClient: underlying,
      );

      final _ = grant.getAuthorizationUrl(config.redirectUri, scopes: config.scopes, state: state);

      return grant.handleAuthorizationResponse(queryParams).then((client) {
        prefs.setString(Keys._credentials, client.credentials.toJson());
        return client;
      }).whenComplete(() {
        prefs.remove(Keys._codeVerifier);
        prefs.remove(Keys._state);
      });
    } else {
      return Future.error(StateError('Cannot handle authorization response; no code verifier found'));
    }
  }

  static Future<oauth2.Client?> getClient() async {
    final credentials = await _getCredentials();

    if (credentials != null && (!credentials.isExpired || credentials.canRefresh)) {
      return oauth2.Client(credentials);
    } else {
      return null;
    }
  }

  static Future<void> discardCredentials() async {
    final prefs = await SharedPreferences.getInstance();
    prefs.remove(Keys._credentials);
  }

  static Future<oauth2.Credentials?> _getCredentials() async {
    final prefs = await SharedPreferences.getInstance();
    final stored = prefs.getString(Keys._credentials);

    if (stored != null) {
      return oauth2.Credentials.fromJson(stored);
    } else {
      return null;
    }
  }

  static String _generateRandomString(int size) {
    final random = Random.secure();
    return List.generate(size, (_) => _chars[random.nextInt(_chars.length)]).join();
  }

  static const String _chars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
}

class OAuthConfig {
  OAuthConfig({
    required this.authorizationEndpoint,
    required this.tokenEndpoint,
    required this.clientId,
    required this.redirectUri,
    required this.scopes,
  });

  Uri authorizationEndpoint;
  Uri tokenEndpoint;
  String clientId;
  Uri redirectUri;
  List<String> scopes;
}

class Keys {
  static const String _codeVerifier = 'stasis.server_ui.oauth.code_verifier';
  static const String _state = 'stasis.server_ui.oauth.state';
  static const String _credentials = 'stasis.server_ui.oauth.credentials';
}

class Defaults {
  static const int codeVerifierSize = 128;
  static const int stateSize = 64;
}
