import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/api/app_processes.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/client_bootstrap_form.dart';
import 'package:stasis_client_ui/pages/components/client_credentials_reset_form.dart';
import 'package:stasis_client_ui/pages/components/client_logs.dart';
import 'package:stasis_client_ui/pages/components/client_pull_device_secret_form.dart';
import 'package:stasis_client_ui/pages/components/client_push_device_secret_form.dart';
import 'package:stasis_client_ui/pages/components/client_reencrypt_device_secret_form.dart';
import 'package:stasis_client_ui/pages/components/client_regenerate_api_certificate_form.dart';

class CredentialsForm extends StatefulWidget {
  const CredentialsForm({
    super.key,
    required this.processes,
    required this.loginHandler,
  });

  final AppProcesses processes;
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

            Widget renderOptionSection({required String title}) {
              return Padding(
                padding: EdgeInsetsGeometry.only(left: 8.0),
                child: Text(
                  title,
                  style: theme.textTheme.labelMedium?.copyWith(fontStyle: FontStyle.italic),
                ),
              );
            }

            Widget renderOption({
              required IconData icon,
              required String title,
              required String subtitle,
              void Function()? onTap,
            }) {
              return ListTile(
                dense: true,
                enabled: onTap != null,
                visualDensity: density,
                leading: Icon(icon),
                title: Text(title, style: theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.bold)),
                subtitle: Text(subtitle, style: theme.textTheme.bodySmall),
                onTap: onTap,
              );
            }

            void showCommandDialog({required Widget child}) {
              showDialog(
                context: context,
                barrierDismissible: false,
                builder: (context) {
                  return SimpleDialog(
                    contentPadding: const EdgeInsets.all(16.0),
                    children: [
                      SizedBox(
                        width: MediaQuery.of(context).size.width * 0.5,
                        child: child,
                      ),
                    ],
                  );
                },
              );
            }

            showDialog(
              context: context,
              builder: (_) => SimpleDialog(
                contentPadding: EdgeInsetsGeometry.all(8),
                title: Row(
                  children: [
                    const Icon(Icons.settings),
                    const Padding(padding: EdgeInsets.only(left: 4.0)),
                    Text('More Options', style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold)),
                  ],
                ),
                children: [
                  renderOptionSection(title: 'Secrets'),
                  renderOption(
                    icon: Icons.lock,
                    title: 'Re-encrypt device secret',
                    subtitle:
                        'If your password was changed on a different device, this allows decrypting the '
                        'secret of this device using the old password and re-encrypting it using the new one.',
                    onTap: () {
                      showCommandDialog(
                        child: ClientReEncryptDeviceSecretForm(
                          title: 'Re-encrypt device secret',
                          processes: widget.processes,
                          callback: (isSuccessful) {
                            if (isSuccessful) Navigator.pop(context);
                          },
                          onCancelled: () => Navigator.pop(context),
                        ),
                      );
                    },
                  ),
                  renderOption(
                    icon: Icons.cloud_upload,
                    title: 'Push device secret',
                    subtitle:
                        'Send the current client secret to the server; '
                        'the secret is always encrypted locally before being sent.',
                    onTap: () {
                      showCommandDialog(
                        child: ClientPushDeviceSecretForm(
                          title: 'Push device secret',
                          processes: widget.processes,
                          callback: (isSuccessful) {
                            if (isSuccessful) Navigator.pop(context);
                          },
                          onCancelled: () => Navigator.pop(context),
                        ),
                      );
                    },
                  ),
                  renderOption(
                    icon: Icons.cloud_download,
                    title: 'Pull device secret',
                    subtitle: 'Retrieve the client secret from the server.',
                    onTap: () {
                      showCommandDialog(
                        child: ClientPullDeviceSecretForm(
                          title: 'Pull device secret',
                          processes: widget.processes,
                          callback: (isSuccessful) {
                            if (isSuccessful) Navigator.pop(context);
                          },
                          onCancelled: () => Navigator.pop(context),
                        ),
                      );
                    },
                  ),
                  renderOptionSection(title: 'Credentials'),
                  renderOption(
                    icon: Icons.password,
                    title: 'Reset user password',
                    subtitle:
                        'Reset the current user\'s password; if that is not possible, contact your system administrator.',
                    onTap: () {
                      showCommandDialog(
                        child: ClientCredentialsResetForm(
                          title: 'Reset user password',
                          processes: widget.processes,
                          callback: (isSuccessful) {
                            if (isSuccessful) Navigator.pop(context);
                          },
                          onCancelled: () => Navigator.pop(context),
                        ),
                      );
                    },
                  ),
                  renderOptionSection(title: 'Maintenance'),
                  renderOption(
                    icon: Icons.restart_alt,
                    title: 'Reinitialize device',
                    subtitle: 'Re-run the bootstrap process for this device.',
                    onTap: () {
                      showCommandDialog(
                        child: ClientBootstrapForm(
                          title: 'Reinitialize device',
                          processes: widget.processes,
                          callback: (isSuccessful) {
                            if (isSuccessful) Navigator.pop(context);
                          },
                          onCancelled: () => Navigator.pop(context),
                        ),
                      );
                    },
                  ),
                  renderOption(
                    icon: Icons.security,
                    title: 'Regenerate API certificate',
                    subtitle: 'Regenerate the TLS certificate used by the client\'s local API.',
                    onTap: () {
                      showCommandDialog(
                        child: ClientRegenerateApiCertificateForm(
                          title: 'Regenerate API certificate',
                          processes: widget.processes,
                          callback: (isSuccessful) {
                            if (isSuccessful) Navigator.pop(context);
                          },
                          onCancelled: () => Navigator.pop(context),
                        ),
                      );
                    },
                  ),
                  renderOption(
                    icon: Icons.terminal,
                    title: 'View client logs',
                    subtitle: 'Show the latest client logs.',
                    onTap: () {
                      final logsDir = ClientLogs.getLogsDir();
                      showDialog(
                        context: context,
                        builder: (context) => buildPage<List<String>>(
                          of: () => ClientLogs.loadLogsFromFile(path: logsDir),
                          builder: (context, logs) {
                            return SimpleDialog(
                              title: Text('Logs from [${logsDir ?? 'none'}]', style: theme.textTheme.titleSmall),
                              contentPadding: const EdgeInsets.all(16),
                              children: [ClientLogs(stdout: logs)],
                            );
                          },
                        ),
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
        children: [
          usernameField,
          passwordField,
          button,
          moreOptions,
        ].map((e) => Padding(padding: const EdgeInsets.all(8.0), child: e)).toList(),
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
