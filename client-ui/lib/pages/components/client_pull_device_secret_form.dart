import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:stasis_client_ui/api/app_processes.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/client_logs.dart';
import 'package:stasis_client_ui/pages/components/entity_form.dart';
import 'package:stasis_client_ui/pages/components/forms/boolean_field.dart';

class ClientPullDeviceSecretForm extends StatefulWidget {
  const ClientPullDeviceSecretForm({
    super.key,
    this.title,
    required this.processes,
    required this.callback,
    required this.onCancelled,
  });

  final String? title;
  final AppProcesses processes;

  final void Function(bool isSuccessful) callback;
  final void Function() onCancelled;

  @override
  State createState() {
    return _ClientPullDeviceSecretFormState();
  }
}

class _ClientPullDeviceSecretFormState extends State<ClientPullDeviceSecretForm> {
  final _key = GlobalKey<FormState>();

  bool _showExtra = false;
  bool _processingResponse = false;
  Exception? _operationFailure;

  final _currentUsernameController = TextEditingController();
  final _currentPasswordController = TextEditingController();
  final _remotePasswordController = TextEditingController();
  final _enableDebuggingController = BooleanController(initialValue: false);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final logo = createLogo(size: 128.0);

    final currentUsernameField = TextFormField(
      enabled: !_processingResponse,
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        prefixIcon: Tooltip(
          message: 'Current user name (for connection to the server).',
          child: Icon(Icons.help_center),
        ),
        labelText: 'Current User',
      ),
      controller: _currentUsernameController,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'User name cannot be empty';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) async => _operationHandler(),
    );

    final currentPasswordField = TextFormField(
      enabled: !_processingResponse,
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        prefixIcon: Tooltip(
          message:
              'Current user password (for connection to the server). '
              'Your password is never stored, sent to the server or shared with other services.',
          child: Icon(Icons.help_center),
        ),
        labelText: 'Current Password',
      ),
      obscureText: true,
      enableSuggestions: false,
      autocorrect: false,
      controller: _currentPasswordController,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'Password cannot be empty';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) async => _operationHandler(),
    );

    final remotePasswordField = TextFormField(
      enabled: !_processingResponse,
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        prefixIcon: Tooltip(
          message:
              'Password override if the remote secret has a different password. '
              'Your password is never stored, sent to the server or shared with other services.',
          child: Icon(Icons.help_center),
        ),
        labelText: 'Remote Password (optional)',
      ),
      obscureText: true,
      enableSuggestions: false,
      autocorrect: false,
      controller: _remotePasswordController,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      onFieldSubmitted: (_) async => _operationHandler(),
    );

    final enableDebuggingField = booleanField(
      title: 'Enable debugging',
      hint: 'Enables debug logging during the operation.',
      controller: _enableDebuggingController,
      enabled: !_processingResponse,
    );

    Widget pullButton = FilledButton(
      onPressed: _processingResponse ? null : _operationHandler,
      child: const Text('Pull'),
    );

    void toggleExtra() {
      setState(() => _showExtra = !_showExtra);
    }

    Widget cancelButton = Visibility(
      visible: !_processingResponse,
      child: Row(
        mainAxisSize: MainAxisSize.max,
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          Tooltip(
            message: 'Cancel',
            child: IconButton(
              onPressed: _processingResponse ? null : widget.onCancelled,
              icon: Icon(Icons.close),
            ),
          ),
        ],
      ),
    );

    Widget controlButtons;
    if (_processingResponse) {
      controlButtons = const CircularProgressIndicator();
    } else if (_operationFailure != null) {
      final errorStyle = theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.error);
      controlButtons = Row(
        children: [
          Flexible(
            flex: 4,
            fit: FlexFit.tight,
            child: RichText(
              text: TextSpan(
                children: _operationFailure is ProcessExpectationFailure
                    ? [
                        TextSpan(text: '$_operationFailure (', style: errorStyle),
                        TextSpan(
                          text: 'show more',
                          style: errorStyle?.copyWith(decoration: TextDecoration.underline),
                          recognizer: TapGestureRecognizer()
                            ..onTap = () => _showOperationFailure(_operationFailure as ProcessExpectationFailure),
                        ),
                        TextSpan(text: ')', style: errorStyle),
                      ]
                    : [
                        TextSpan(text: _operationFailure.toString(), style: errorStyle),
                      ],
              ),
            ),
          ),
          Flexible(
            flex: 1,
            fit: FlexFit.tight,
            child: Tooltip(
              message: 'Clear error',
              child: OutlinedButton(
                onPressed: () => setState(() => _operationFailure = null),
                child: Icon(Icons.clear, color: theme.colorScheme.error),
              ),
            ),
          ),
        ],
      );
    } else {
      controlButtons = Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [pullButton],
      );
    }

    List<Widget> content = [currentUsernameField, currentPasswordField];

    List<Widget> extra;
    final extraButton = IconButton(
      visualDensity: VisualDensity.compact,
      onPressed: _processingResponse ? null : toggleExtra,
      icon: Icon(_showExtra ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down),
    );

    if (_showExtra) {
      extra = [
        Padding(padding: const EdgeInsets.all(8.0), child: remotePasswordField),
        enableDebuggingField,
        extraButton,
      ];
    } else {
      extra = [extraButton];
    }

    List<Widget> header;
    if (widget.title != null) {
      header = [
        Align(
          alignment: Alignment.topLeft,
          child: Padding(
            padding: EdgeInsetsGeometry.only(top: 8.0),
            child: Text(
              widget.title!,
              style: theme.textTheme.titleMedium,
            ),
          ),
        ),
      ];
    } else {
      header = [];
    }

    return Form(
      key: _key,
      child: Column(
        children: [
          Stack(
            children:
                header +
                [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Padding(
                        padding: EdgeInsetsGeometry.only(top: widget.title != null ? 24.0 : 0.0),
                        child: logo,
                      ),
                    ],
                  ),
                  Align(alignment: Alignment.topRight, child: cancelButton),
                ],
          ),
          Padding(
            padding: EdgeInsetsGeometry.only(bottom: extra.isNotEmpty ? 0.0 : 48.0),
            child: Column(
              mainAxisSize: MainAxisSize.max,
              mainAxisAlignment: MainAxisAlignment.end,
              children:
                  (content.map((e) => Padding(padding: const EdgeInsets.all(8.0), child: e)).toList() +
                  extra.map((e) => Padding(padding: const EdgeInsets.all(0), child: e)).toList()),
            ),
          ),
          controlButtons,
        ],
      ),
    );
  }

  @override
  void dispose() {
    _currentUsernameController.dispose();
    _currentPasswordController.dispose();
    _remotePasswordController.dispose();
    _enableDebuggingController.dispose();
    super.dispose();
  }

  void _operationHandler() async {
    _processing(true);

    if (_key.currentState?.validate() ?? false) {
      final currentUsername = _currentUsernameController.text;
      final currentPassword = _currentPasswordController.text;
      final remotePassword = _remotePasswordController.text.isNotEmpty ? _remotePasswordController.text : null;
      final enableDebugging = _enableDebuggingController.value;

      try {
        await widget.processes
            .pullDeviceSecret(
              currentUsername: currentUsername,
              currentPassword: currentPassword,
              remotePassword: remotePassword,
              enableDebugging: enableDebugging,
            )
            .then((_) {
              final ctx = context;
              if (ctx.mounted) _showSnackBar(ctx, message: 'Client operation successful');
              widget.callback(true);
            })
            .onError<Exception>((e, _) {
              _processing(false);
              _operationFailure = e;
              widget.callback(false);
            });
      } on Exception catch (e) {
        if (!mounted) return;
        _showSnackBar(context, message: e.toString());
        _processing(false);
      }
    } else {
      _processing(false);
    }
  }

  void _processing(bool value) {
    setState(() {
      _processingResponse = value;
      if (value) {
        _operationFailure = null;
      }
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

  void _showOperationFailure(ProcessExpectationFailure e) {
    showDialog(
      context: context,
      builder: (context) {
        return SimpleDialog(
          title: Text(e.message),
          contentPadding: const EdgeInsets.all(16),
          children: [ClientLogs(stdout: e.stdout, stderr: e.stderr)],
        );
      },
    );
  }
}
