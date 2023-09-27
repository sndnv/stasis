import 'package:stasis_client_ui/api/api_client.dart';
import 'package:flutter/material.dart';

class CredentialsForm extends StatefulWidget {
  const CredentialsForm({
    Key? key,
    required this.loginHandler,
  }) : super(key: key);

  final Future<void> Function(String, String) loginHandler;

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
      onFieldSubmitted: (_) => _loginHandler(),
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
      onFieldSubmitted: (_) async => _loginHandler(),
    );

    Widget button;
    if (_processingResponse) {
      button = const CircularProgressIndicator();
    } else {
      button = ElevatedButton(
        onPressed: _processingResponse ? null : _loginHandler,
        child: const Text('Login'),
      );
    }

    return Form(
      key: _key,
      child: Column(
        children: [usernameField, passwordField, button]
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

  void _loginHandler() async {
    _processing(true);

    if (_key.currentState?.validate() ?? false) {
      final username = _usernameController.text;
      final password = _passwordController.text;

      try {
        await widget.loginHandler(username, password);
      } on AuthenticationFailure {
        _processing(false, denied: true);
        _key.currentState?.validate();
      } on Exception catch (e) {
        if (!mounted) return;
        _showSnackBar(context, message: e.toString());
        _processing(false);
      }
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

  void _showSnackBar(BuildContext context, {required String message}) {
    final messenger = ScaffoldMessenger.of(context);
    messenger.showSnackBar(
      SnackBar(
        content: SelectionArea(child: Text(message)),
        duration: const Duration(seconds: 10),
        showCloseIcon: true,
      ),
    );
  }
}
