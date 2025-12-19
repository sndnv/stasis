import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:stasis_client_ui/api/app_processes.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/client_logs.dart';
import 'package:stasis_client_ui/pages/components/entity_form.dart';
import 'package:stasis_client_ui/pages/components/forms/boolean_field.dart';

class ClientBootstrapForm extends StatefulWidget {
  const ClientBootstrapForm({
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
    return _ClientBootstrapFormState();
  }
}

class _ClientBootstrapFormState extends State<ClientBootstrapForm> {
  final _key = GlobalKey<FormState>();

  int _page = 0;
  bool _showExtra = false;
  bool _processingResponse = false;
  Exception? _bootstrapFailure;

  final _serverController = TextEditingController();
  final _codeController = TextEditingController();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _passwordConfirmationController = TextEditingController();
  final _acceptSelfSignedController = BooleanController(initialValue: false);
  final _recreateFilesController = BooleanController(initialValue: false);
  final _enableDebuggingController = BooleanController(initialValue: false);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final logo = createLogo(size: 128.0);

    final serverField = TextFormField(
      enabled: !_processingResponse,
      decoration: InputDecoration(
        border: const OutlineInputBorder(),
        prefixIcon: Tooltip(
          message:
              'Make sure you are entering the URL of a trusted server and '
              'that it is entered exactly as shown by that server.',
          child: Icon(Icons.warning),
        ),
        labelText: 'Bootstrap Server URL',
        prefixText: 'https://',
      ),
      controller: _serverController,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'Server bootstrap URL must be provided';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) async => _bootstrapHandler(),
    );

    final codeField = TextFormField(
      enabled: !_processingResponse,
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        prefixIcon: Tooltip(
          message:
              'This code is required for securely initializing the device and '
              'should be entered exactly as shown by the server.',
          child: Icon(Icons.help_center),
        ),
        labelText: 'Bootstrap Code',
      ),
      controller: _codeController,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'Bootstrap code cannot be empty';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) async => _bootstrapHandler(),
    );

    final usernameField = TextFormField(
      enabled: !_processingResponse,
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        prefixIcon: Tooltip(
          message: 'User name (for connection to the server, when pulling secrets).',
          child: Icon(Icons.help_center),
        ),
        labelText: 'User',
      ),
      controller: _usernameController,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'User name cannot be empty';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) async => _bootstrapHandler(),
    );

    final passwordField = TextFormField(
      enabled: !_processingResponse,
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        prefixIcon: Tooltip(
          message:
              'This password is required for encrypting the secrets used by this device. '
              'Your password is never stored, sent to the server or shared with other services.',
          child: Icon(Icons.help_center),
        ),
        labelText: 'Password',
      ),
      obscureText: true,
      enableSuggestions: false,
      autocorrect: false,
      controller: _passwordController,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'Password cannot be empty';
        } else if (value != _passwordConfirmationController.text) {
          return 'Passwords do not match';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) async => _bootstrapHandler(),
    );

    final passwordConfirmationField = TextFormField(
      enabled: !_processingResponse,
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        prefixIcon: Tooltip(
          message: 'Your password is never stored, sent to the server or shared with other services.',
          child: Icon(Icons.help_center),
        ),
        labelText: 'Confirm Password',
      ),
      obscureText: true,
      enableSuggestions: false,
      autocorrect: false,
      controller: _passwordConfirmationController,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'Password cannot be empty';
        } else if (value != _passwordController.text) {
          return 'Passwords do not match';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) async => _bootstrapHandler(),
    );

    final acceptSelfSignedField = booleanField(
      title: 'Allow self-signed certificates',
      hint: 'Accept any self-signed server TLS certificate (NOT recommended).',
      controller: _acceptSelfSignedController,
      enabled: !_processingResponse,
    );

    final recreateFilesField = booleanField(
      title: 'Re-create existing files',
      hint: 'Force the bootstrap process to recreate all configuration files, even if they already exist.',
      controller: _recreateFilesController,
      enabled: !_processingResponse,
    );

    final enableDebuggingField = booleanField(
      title: 'Enable debugging',
      hint: 'Enables debug logging during the bootstrap process.',
      controller: _enableDebuggingController,
      enabled: !_processingResponse,
    );

    Widget bootstrapButton = FilledButton(
      onPressed: _processingResponse ? null : _bootstrapHandler,
      child: const Text('Bootstrap'),
    );

    void previousPage() {
      setState(() => _page -= 1);
    }

    void nextPage() {
      if (_key.currentState?.validate() == true) {
        setState(() => _page += 1);
      }
    }

    void toggleExtra() {
      setState(() => _showExtra = !_showExtra);
    }

    Widget previousButton = Visibility(
      visible: _page > 0,
      child: OutlinedButton(
        onPressed: _processingResponse ? null : previousPage,
        child: const Text('Previous'),
      ),
    );

    Widget pageNumber = Text('${_page + 1} of 4', textAlign: TextAlign.center);

    Widget nextButton = ElevatedButton(
      onPressed: _processingResponse ? null : nextPage,
      child: const Text('Next'),
    );

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
    } else if (_bootstrapFailure != null) {
      final errorStyle = theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.error);
      controlButtons = Row(
        children: [
          Flexible(
            flex: 4,
            fit: FlexFit.tight,
            child: RichText(
              text: TextSpan(
                children: _bootstrapFailure is ProcessExpectationFailure
                    ? [
                        TextSpan(text: '$_bootstrapFailure (', style: errorStyle),
                        TextSpan(
                          text: 'show more',
                          style: errorStyle?.copyWith(decoration: TextDecoration.underline),
                          recognizer: TapGestureRecognizer()
                            ..onTap = () => _showBootstrapFailure(_bootstrapFailure as ProcessExpectationFailure),
                        ),
                        TextSpan(text: ')', style: errorStyle),
                      ]
                    : [
                        TextSpan(text: _bootstrapFailure.toString(), style: errorStyle),
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
                onPressed: () => setState(() => _bootstrapFailure = null),
                child: Icon(Icons.clear, color: theme.colorScheme.error),
              ),
            ),
          ),
        ],
      );
    } else {
      controlButtons = Row(
        children: [
          Flexible(flex: 2, fit: FlexFit.tight, child: previousButton),
          Flexible(flex: 1, fit: FlexFit.tight, child: pageNumber),
          Flexible(flex: 2, fit: FlexFit.tight, child: _page > 2 ? bootstrapButton : nextButton),
        ],
      );
    }

    List<Widget> content;
    if (_page == 0) {
      content = [serverField];
    } else if (_page == 1) {
      content = [usernameField];
    } else if (_page == 2) {
      content = [passwordField, passwordConfirmationField];
    } else {
      content = [codeField];
    }

    List<Widget> extraFields;
    if (_page == 0) {
      extraFields = [acceptSelfSignedField];
    } else if (_page == 1) {
      extraFields = [];
    } else if (_page == 2) {
      extraFields = [];
    } else {
      extraFields = [recreateFilesField, enableDebuggingField];
    }

    List<Widget> extra;
    if (extraFields.isNotEmpty) {
      final extraButton = IconButton(
        visualDensity: VisualDensity.compact,
        onPressed: _processingResponse ? null : toggleExtra,
        icon: Icon(_showExtra ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down),
      );

      if (_showExtra) {
        extra = extraFields + [extraButton];
      } else {
        extra = [extraButton];
      }
    } else {
      extra = [];
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
    _serverController.dispose();
    _codeController.dispose();
    _usernameController.dispose();
    _passwordController.dispose();
    _passwordConfirmationController.dispose();
    _acceptSelfSignedController.dispose();
    _recreateFilesController.dispose();
    _enableDebuggingController.dispose();
    super.dispose();
  }

  void _bootstrapHandler() async {
    _processing(true);

    if (_key.currentState?.validate() ?? false) {
      final server = _serverController.text;
      final code = _codeController.text;
      final username = _usernameController.text;
      final password = _passwordController.text;
      final acceptSelfSigned = _acceptSelfSignedController.value;
      final recreateFiles = _recreateFilesController.value;
      final enableDebugging = _enableDebuggingController.value;

      try {
        await widget.processes
            .bootstrap(
              server: 'https://$server',
              code: code,
              username: username,
              password: password,
              acceptSelfSigned: acceptSelfSigned,
              recreateFiles: recreateFiles,
              enableDebugging: enableDebugging,
            )
            .then((_) {
              final ctx = context;
              if (ctx.mounted) _showSnackBar(ctx, message: 'Client bootstrap successful');
              widget.callback(true);
            })
            .onError<Exception>((e, _) {
              _processing(false);
              _bootstrapFailure = e;
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
        _bootstrapFailure = null;
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

  void _showBootstrapFailure(ProcessExpectationFailure e) {
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
