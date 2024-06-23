import 'package:flutter/material.dart';
import 'package:stasis_client_ui/api/api_client.dart';

class UpdateUserCredentialsForm extends StatefulWidget {
  const UpdateUserCredentialsForm({
    super.key,
    required this.title,
    required this.icon,
    required this.isSecret,
    required this.action,
  });

  final String title;
  final IconData icon;
  final bool isSecret;
  final Future<void> Function(String, String) action;

  @override
  State createState() {
    return _UpdateUserCredentialsFormState();
  }
}

class _UpdateUserCredentialsFormState extends State<UpdateUserCredentialsForm> {
  final _key = GlobalKey<FormState>();
  final _currentPasswordController = TextEditingController();
  final _currentPasswordConfirmationController = TextEditingController();
  bool _currentPasswordMismatched = false;
  bool _currentPasswordInvalid = false;

  final _updatedCredentialController = TextEditingController();
  final _updatedCredentialConfirmationController = TextEditingController();
  bool _updatedCredentialsMismatched = false;

  bool _processingResponse = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final currentPasswordField = TextFormField(
      enabled: !_processingResponse,
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        prefixIcon: Icon(Icons.lock),
        labelText: 'Current Password',
      ),
      obscureText: true,
      enableSuggestions: false,
      autocorrect: false,
      controller: _currentPasswordController,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'Current password cannot be empty';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) async => _actionHandler(),
    );

    final currentPasswordConfirmationField = TextFormField(
      enabled: !_processingResponse,
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        prefixIcon: Icon(Icons.lock),
        labelText: 'Confirm Current Password',
      ),
      obscureText: true,
      enableSuggestions: false,
      autocorrect: false,
      controller: _currentPasswordConfirmationController,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'Current password cannot be empty';
        } else if (_currentPasswordMismatched) {
          return 'Current passwords do not match';
        } else if (_currentPasswordInvalid) {
          return 'Invalid current password provided';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) async => _actionHandler(),
    );

    final updatedCredentialField = TextFormField(
      enabled: !_processingResponse,
      decoration: InputDecoration(
        border: const OutlineInputBorder(),
        prefixIcon: Icon(widget.icon),
        labelText: 'New ${widget.title}',
      ),
      obscureText: widget.isSecret,
      enableSuggestions: false,
      autocorrect: false,
      controller: _updatedCredentialController,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return '${widget.title} cannot be empty';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) async => _actionHandler(),
    );

    final updatedCredentialConfirmationField = TextFormField(
      enabled: !_processingResponse,
      decoration: InputDecoration(
        border: const OutlineInputBorder(),
        prefixIcon: Icon(widget.icon),
        labelText: 'Confirm New ${widget.title}',
      ),
      obscureText: widget.isSecret,
      enableSuggestions: false,
      autocorrect: false,
      controller: _updatedCredentialConfirmationController,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return '${widget.title} cannot be empty';
        } else if (_updatedCredentialsMismatched) {
          return 'New ${widget.title.toLowerCase()}s do not match';
        } else {
          return null;
        }
      },
      onFieldSubmitted: (_) async => _actionHandler(),
    );

    Widget button;
    if (_processingResponse) {
      button = const CircularProgressIndicator();
    } else {
      button = ElevatedButton(
        onPressed: _processingResponse ? null : _actionHandler,
        child: const Text('Update'),
      );
    }

    return Form(
      key: _key,
      child: Column(
        mainAxisSize: MainAxisSize.max,
        children: [
          currentPasswordField,
          currentPasswordConfirmationField,
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 96.0),
            child: Divider(color: theme.primaryColor, thickness: 1.0),
          ),
          updatedCredentialField,
          updatedCredentialConfirmationField,
          button,
        ].map((e) => Padding(padding: const EdgeInsets.all(8.0), child: e)).toList(),
      ),
    );
  }

  @override
  void dispose() {
    _currentPasswordController.dispose();
    _currentPasswordConfirmationController.dispose();
    _updatedCredentialController.dispose();
    _updatedCredentialConfirmationController.dispose();
    super.dispose();
  }

  void _actionHandler() async {
    _processing(true);

    if (_key.currentState?.validate() ?? false) {
      final currentPassword = _currentPasswordController.text;
      final currentPasswordConfirmation = _currentPasswordConfirmationController.text;
      final updatedCredential = _updatedCredentialController.text;
      final updatedCredentialConfirmation = _updatedCredentialConfirmationController.text;

      final mismatchedCurrentPassword = currentPassword != currentPasswordConfirmation;
      final mismatchedUpdatedCredentials = updatedCredential != updatedCredentialConfirmation;

      if (mismatchedCurrentPassword || mismatchedUpdatedCredentials) {
        _processing(
          false,
          mismatchedCurrentPassword: mismatchedCurrentPassword,
          mismatchedUpdatedCredentials: mismatchedUpdatedCredentials,
        );
        _key.currentState?.validate();
      } else {
        try {
          await widget.action(currentPassword, updatedCredential);
          if (!mounted) return;
          _showSnackBar(context, message: 'Successfully updated ${widget.title.toLowerCase()}.');
          _processing(false);
          _currentPasswordController.text = '';
          _currentPasswordConfirmationController.text = '';
          _updatedCredentialController.text = '';
          _updatedCredentialConfirmationController.text = '';
        } on ConflictFailure {
          _processing(false, currentPasswordInvalid: true);
          _key.currentState?.validate();
        } on Exception catch (e) {
          if (!mounted) return;
          _showSnackBar(context, message: 'Failed to update ${widget.title.toLowerCase()}: [${e.toString()}]');
          _processing(false);
        }
      }
    } else {
      _processing(false);
    }
  }

  void _processing(
    bool value, {
    bool mismatchedCurrentPassword = false,
    bool mismatchedUpdatedCredentials = false,
    bool currentPasswordInvalid = false,
  }) {
    setState(() {
      _processingResponse = value;
      _currentPasswordMismatched = mismatchedCurrentPassword;
      _updatedCredentialsMismatched = mismatchedUpdatedCredentials;
      _currentPasswordInvalid = currentPasswordInvalid;
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
