import 'dart:convert';
import 'dart:html';
import 'dart:js' as js;

import 'package:flutter/material.dart';
import 'package:identity_ui/api/oauth.dart';
import 'package:identity_ui/pages/authorize/derived_passwords.dart';
import 'package:shared_preferences/shared_preferences.dart';

class CredentialsForm extends StatefulWidget {
  const CredentialsForm({
    super.key,
    required this.requestedScopes,
    required this.authorizationEndpoint,
    required this.oauthConfig,
    required this.passwordDerivationConfig,
    required this.prefs,
  });

  final List<String> requestedScopes;
  final Uri authorizationEndpoint;
  final OAuthConfig oauthConfig;
  final UserAuthenticationPasswordDerivationConfig passwordDerivationConfig;
  final SharedPreferences prefs;

  @override
  State createState() {
    return _CredentialsFormState();
  }
}

class _CredentialsFormState extends State<CredentialsForm> {
  final _key = GlobalKey<FormState>();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _userSaltController = TextEditingController();
  bool _processingResponse = false;
  bool _accessDenied = false;
  bool _extrasShown = false;

  @override
  void initState() {
    super.initState();
    _extrasShown = (widget.prefs.getString(Keys._userSalt)?.trim() ?? '').isEmpty;
  }

  @override
  Widget build(BuildContext context) {
    final usernameField = TextFormField(
      enabled: !_processingResponse,
      decoration: const InputDecoration(
        prefixIcon: Icon(Icons.person),
        border: OutlineInputBorder(),
        labelText: 'Username',
      ),
      controller: _usernameController,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'Username cannot be empty';
        } else if (_accessDenied) {
          return '';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) => _authorizationHandler(),
    );

    final passwordField = TextFormField(
      enabled: !_processingResponse,
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        prefixIcon: Icon(Icons.lock),
        labelText: 'Password',
      ),
      obscureText: true,
      enableSuggestions: false,
      autocorrect: false,
      controller: _passwordController,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'Password cannot be empty';
        } else if (_accessDenied) {
          return 'Invalid credentials specified';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) => _authorizationHandler(),
    );

    final storedUserSalt = widget.prefs.getString(Keys._userSalt)?.trim() ?? '';
    if (storedUserSalt.isNotEmpty) {
      _userSaltController.text = storedUserSalt;
    }

    final userSaltField = TextFormField(
      enabled: !_processingResponse,
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        prefixIcon: Icon(Icons.lock),
        labelText: 'User Salt',
      ),
      obscureText: true,
      enableSuggestions: false,
      autocorrect: false,
      controller: _userSaltController,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'User salt cannot be empty';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) => _authorizationHandler(),
      onChanged: (value) => widget.prefs.setString(Keys._userSalt, value),
    );

    final userSaltFieldVisibility = Visibility(
      visible: _extrasShown,
      child: userSaltField,
    );

    final rejectButton = TextButton(
      onPressed: _processingResponse ? null : _rejectionHandler,
      child: const Text('Reject'),
    );

    final authorizeButton = ElevatedButton(
      onPressed: _processingResponse ? null : _authorizationHandler,
      child: const Text('Authorize'),
    );

    final extrasButton = Tooltip(
      message: _extrasShown ? 'Hide extra fields' : 'Show extra fields',
      child: TextButton(
        onPressed: _toggleExtras,
        child: Icon(_extrasShown ? Icons.arrow_drop_up_sharp : Icons.arrow_drop_down_sharp),
      ),
    );

    Widget buttons;
    if (_processingResponse) {
      buttons = const CircularProgressIndicator();
    } else {
      buttons = Row(
        mainAxisSize: MainAxisSize.min,
        children: [rejectButton, const Padding(padding: EdgeInsets.only(left: 8.0, right: 8.0)), authorizeButton],
      );
    }

    Widget withPadding(Widget widget) {
      if (widget == extrasButton || !_extrasShown && widget == userSaltFieldVisibility) {
        return widget;
      } else {
        return Padding(padding: const EdgeInsets.all(8.0), child: widget);
      }
    }

    return Form(
      key: _key,
      child: Column(
        children: (_passwordDerivationRequired()
                ? [usernameField, passwordField, userSaltFieldVisibility, extrasButton, buttons]
                : [usernameField, passwordField, buttons])
            .map((e) => withPadding(e))
            .toList(),
      ),
    );
  }

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    _userSaltController.dispose();
    super.dispose();
  }

  void _rejectionHandler() {
    _processing(true);

    final redirectUriParameter = Uri.base.queryParameters['redirect_uri'];

    if (redirectUriParameter != null) {
      const error = 'access_denied';
      const errorDescription = 'The resource owner or authorization server denied the request.';

      final redirectUri = Uri.parse(redirectUriParameter)
          .replace(queryParameters: {'error': error, 'error_description': errorDescription});

      window.location.assign(redirectUri.toString());
    }
  }

  void _authorizationHandler() {
    _processing(true);

    void callback(String? error) {
      if (error == 'access_denied') {
        _processing(false, denied: true);
        _key.currentState?.validate();
      }
    }

    if (_key.currentState?.validate() ?? false) {
      final params = Uri.base.queryParameters;
      final uri = widget.authorizationEndpoint.replace(queryParameters: params);
      final username = _usernameController.text;
      final password = _passwordController.text;
      final userSalt = _userSaltController.text;

      final futureAuthenticationPassword = _passwordDerivationRequired()
          ? DerivedPasswords.deriveHashedUserAuthenticationPassword(
              password: password,
              saltPrefix: widget.passwordDerivationConfig.saltPrefix,
              salt: userSalt,
              iterations: widget.passwordDerivationConfig.iterations,
              derivedKeySize: widget.passwordDerivationConfig.secretSize,
            )
          : Future.value(password);

      futureAuthenticationPassword.then(
        (authenticationPassword) {
          final authorization = 'Basic ${base64Encode(utf8.encode('$username:$authenticationPassword'))}';
          js.context.callMethod('sendAuthorization', [uri.toString(), authorization, js.allowInterop(callback)]);
        },
        onError: (e) {
          _showSnackBar(message: 'Failed to generate authentication password: [$e]');
          _processing(false);
        },
      );
    } else {
      _processing(false);
    }
  }

  bool _passwordDerivationRequired() {
    return widget.passwordDerivationConfig.enabled &&
        !widget.requestedScopes.toSet().containsAll(widget.oauthConfig.scopes);
  }

  void _processing(bool value, {bool denied = false}) {
    setState(() {
      _processingResponse = value;
      _accessDenied = denied;
    });
  }

  void _toggleExtras() {
    setState(() {
      _extrasShown = !_extrasShown;
    });
  }

  void _showSnackBar({required String message}) {
    final messenger = ScaffoldMessenger.of(context);
    messenger.showSnackBar(SnackBar(content: Text(message)));
  }
}

class Keys {
  static const String _userSalt = 'stasis.identity_ui.credentials.user_salt';
}
