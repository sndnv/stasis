import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:stasis_client_ui/api/app_processes.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/client_logs.dart';
import 'package:stasis_client_ui/pages/components/entity_form.dart';
import 'package:stasis_client_ui/pages/components/forms/boolean_field.dart';

class ClientCredentialsResetForm extends StatefulWidget {
  const ClientCredentialsResetForm({
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
    return _ClientCredentialsResetFormState();
  }
}

class _ClientCredentialsResetFormState extends State<ClientCredentialsResetForm> {
  final _key = GlobalKey<FormState>();

  int _page = 0;
  bool _showExtra = false;
  bool _processingResponse = false;
  Exception? _operationFailure;

  final _currentPasswordController = TextEditingController();
  final _newPasswordController = TextEditingController();
  final _newPasswordConfirmationController = TextEditingController();
  final _newSaltController = TextEditingController();
  final _enableDebuggingController = BooleanController(initialValue: false);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final logo = createLogo(size: 128.0);

    final currentPasswordField = TextFormField(
      enabled: !_processingResponse,
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        prefixIcon: Tooltip(
          message:
              'Used for decrypting the device secret before resetting the credentials. '
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

    final newPasswordField = TextFormField(
      enabled: !_processingResponse,
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        prefixIcon: Tooltip(
          message:
              'This password is required for re-encrypting the secret used by the device. '
              'Your password is never stored, sent to the server or shared with other services.',
          child: Icon(Icons.help_center),
        ),
        labelText: 'New Password',
      ),
      obscureText: true,
      enableSuggestions: false,
      autocorrect: false,
      controller: _newPasswordController,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'Password cannot be empty';
        } else if (value != _newPasswordConfirmationController.text) {
          return 'Passwords do not match';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) async => _operationHandler(),
    );

    final newPasswordConfirmationField = TextFormField(
      enabled: !_processingResponse,
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        prefixIcon: Tooltip(
          message: 'Your password is never stored, sent to the server or shared with other services.',
          child: Icon(Icons.help_center),
        ),
        labelText: 'Confirm New Password',
      ),
      obscureText: true,
      enableSuggestions: false,
      autocorrect: false,
      controller: _newPasswordConfirmationController,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'Password cannot be empty';
        } else if (value != _newPasswordController.text) {
          return 'Passwords do not match';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) async => _operationHandler(),
    );

    final newSaltField = TextFormField(
      enabled: !_processingResponse,
      decoration: InputDecoration(
        border: const OutlineInputBorder(),
        prefixIcon: Tooltip(
          message: 'Used for re-encrypting the device secret after resetting the credentials.',
          child: Icon(Icons.help_center),
        ),
        labelText: 'New Salt',
      ),
      controller: _newSaltController,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'Salt cannot be empty';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) async => _operationHandler(),
    );

    final enableDebuggingField = booleanField(
      title: 'Enable debugging',
      hint: 'Enables debug logging during the reset process.',
      controller: _enableDebuggingController,
      enabled: !_processingResponse,
    );

    Widget resetButton = FilledButton(
      onPressed: _processingResponse ? null : _operationHandler,
      child: const Text('Reset'),
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

    Widget pageNumber = Text('${_page + 1} of 3', textAlign: TextAlign.center);

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
        children: [
          Flexible(flex: 2, fit: FlexFit.tight, child: previousButton),
          Flexible(flex: 1, fit: FlexFit.tight, child: pageNumber),
          Flexible(flex: 2, fit: FlexFit.tight, child: _page > 1 ? resetButton : nextButton),
        ],
      );
    }

    List<Widget> content;
    if (_page == 0) {
      content = [currentPasswordField];
    } else if (_page == 1) {
      content = [newPasswordField, newPasswordConfirmationField];
    } else {
      content = [newSaltField];
    }

    List<Widget> extraFields;
    if (_page == 0) {
      extraFields = [];
    } else if (_page == 1) {
      extraFields = [];
    } else {
      extraFields = [enableDebuggingField];
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
    _currentPasswordController.dispose();
    _newPasswordController.dispose();
    _newPasswordConfirmationController.dispose();
    _newSaltController.dispose();
    _enableDebuggingController.dispose();
    super.dispose();
  }

  void _operationHandler() async {
    _processing(true);

    if (_key.currentState?.validate() ?? false) {
      final currentPassword = _currentPasswordController.text;
      final newPassword = _newPasswordController.text;
      final newSalt = _newSaltController.text;
      final enableDebugging = _enableDebuggingController.value;

      try {
        await widget.processes
            .resetUserCredentials(
              currentPassword: currentPassword,
              newPassword: newPassword,
              newSalt: newSalt,
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
