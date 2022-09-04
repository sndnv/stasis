import 'dart:convert';
import 'dart:html';
import 'dart:js' as js;

import 'package:flutter/material.dart';

class CredentialsForm extends StatefulWidget {
  const CredentialsForm({super.key, required this.authorizationEndpoint});

  final Uri authorizationEndpoint;

  @override
  State createState() {
    return _CredentialsFormState();
  }
}

class _CredentialsFormState extends State<CredentialsForm> {
  final _key = GlobalKey<FormState>();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _processingResponse = false;
  bool _accessDenied = false;

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

    final rejectButton = TextButton(
      onPressed: _processingResponse ? null : _rejectionHandler,
      child: const Text('Reject'),
    );

    final authorizeButton = ElevatedButton(
      onPressed: _processingResponse ? null : _authorizationHandler,
      child: const Text('Authorize'),
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

    return Form(
      key: _key,
      child: Column(
        children: [usernameField, passwordField, buttons]
            .map((e) => Padding(padding: const EdgeInsets.all(8.0), child: e))
            .toList(),
      ),
    );
  }

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
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

      final authorization = 'Basic ${base64Encode(utf8.encode('$username:$password'))}';

      js.context.callMethod('sendAuthorization', [uri.toString(), authorization, js.allowInterop(callback)]);
    } else {
      _processing(false);
    }
  }

  void _processing(bool value, {bool denied = false}) {
    setState(() {
      _processingResponse = value;
      _accessDenied = denied;
    });
  }
}
