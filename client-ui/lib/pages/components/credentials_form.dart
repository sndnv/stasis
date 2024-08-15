import 'package:flutter/gestures.dart';
import 'package:stasis_client_ui/api/api_client.dart';
import 'package:flutter/material.dart';

class CredentialsForm extends StatefulWidget {
  const CredentialsForm({
    super.key,
    required this.applicationName,
    required this.loginHandler,
  });

  final String applicationName;
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
    final theme = Theme.of(context);

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

    final moreOptions = RichText(
      text: TextSpan(
        text: 'More Options',
        style: theme.textTheme.bodySmall?.copyWith(decoration: TextDecoration.underline),
        recognizer: TapGestureRecognizer()
          ..onTap = () {
            const density = VisualDensity(
              horizontal: VisualDensity.minimumDensity,
              vertical: VisualDensity.minimumDensity,
            );

            Widget renderOption({
              required String title,
              required String subtitle,
              void Function()? onTap,
            }) {
              return ListTile(
                dense: true,
                enabled: onTap != null,
                visualDensity: density,
                title: Text(title, style: theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.bold)),
                subtitle: Text(subtitle, style: theme.textTheme.bodySmall),
                onTap: onTap,
              );
            }

            void showCommandHintDialog({required String title, required String hint, required String command}) {
              showDialog(
                context: context,
                builder: (_) => SimpleDialog(
                  title: Text(
                    title,
                    style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold),
                  ),
                  children: [
                    Padding(
                      padding: const EdgeInsets.all(12.0),
                      child: Column(
                        mainAxisSize: MainAxisSize.max,
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text(hint),
                          const Padding(padding: EdgeInsets.only(top: 12.0)),
                          SelectionArea(
                            child: Text(
                              command,
                              style: theme.textTheme.labelSmall?.copyWith(fontWeight: FontWeight.bold),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              );
            }

            showDialog(
              context: context,
              builder: (_) => SimpleDialog(
                title: Row(
                  children: [
                    const Icon(Icons.settings),
                    const Padding(padding: EdgeInsets.only(left: 4.0)),
                    Text('More Options', style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold)),
                  ],
                ),
                children: [
                  renderOption(
                    title: 'Re-encrypt device secret',
                    subtitle: 'If your password was changed on a different device, this allows decrypting the '
                        'secret of this device using the old password and re-encrypting it using the new one.',
                    onTap: () {
                      showCommandHintDialog(
                        title: 'Re-encrypt device secret',
                        hint: 'This device secret can be re-encrypted via the CLI by running:',
                        command: '${widget.applicationName} maintenance secret re-encrypt',
                      );
                    },
                  ),
                  renderOption(
                    title: 'Re-initialize device',
                    subtitle: 'Allows re-running the bootstrap process for this device.',
                    onTap: () {
                      showCommandHintDialog(
                        title: 'Re-initialize device',
                        hint: 'This device can be re-initialized via the CLI by running:',
                        command: '${widget.applicationName} bootstrap',
                      );
                    },
                  ),
                  renderOption(
                    title: 'Reset user password',
                    subtitle: 'Allows reset the current user\'s password; '
                        'if that is not possible, contact your system administrator.',
                    onTap: () {
                      showCommandHintDialog(
                        title: 'Reset user password',
                        hint: 'The user\'s password can be reset via the CLI by running:',
                        command: '${widget.applicationName} maintenance credentials reset',
                      );
                    },
                  ),
                ],
              ),
            );
          },
      ),
    );

    return Form(
      key: _key,
      child: Column(
        children: [usernameField, passwordField, button, moreOptions]
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
