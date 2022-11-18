import 'package:flutter/material.dart';
import 'package:identity_ui/api/api_client.dart';
import 'package:identity_ui/model/requests/create_owner.dart';
import 'package:identity_ui/model/requests/update_owner.dart';
import 'package:identity_ui/model/requests/update_owner_credentials.dart';
import 'package:identity_ui/model/resource_owner.dart';
import 'package:identity_ui/pages/authorize/derived_passwords.dart';
import 'package:identity_ui/pages/default/components.dart';
import 'package:identity_ui/pages/manage/components/entity_form.dart';
import 'package:identity_ui/pages/manage/components/entity_table.dart';

class Owners extends StatefulWidget {
  const Owners({
    super.key,
    required this.client,
    required this.passwordDerivationConfig,
  });

  final ApiClient client;
  final UserAuthenticationPasswordDerivationConfig passwordDerivationConfig;

  @override
  State createState() {
    return _OwnersState();
  }
}

class _OwnersState extends State<Owners> {
  @override
  Widget build(BuildContext context) {
    return buildPage<List<ResourceOwner>>(
      of: () => widget.client.getOwners(),
      builder: (context, owners) {
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            EntityTable<ResourceOwner>(
              entities: owners..sort((a, b) => a.username.compareTo(b.username)),
              actions: [
                IconButton(
                  tooltip: 'Create New Resource Owner',
                  onPressed: () => _createOwner(context),
                  icon: const Icon(Icons.add),
                ),
              ],
              header: const Text('Owners'),
              columns: const [
                DataColumn(label: Text('Username')),
                DataColumn(label: Text('Subject')),
                DataColumn(label: Text('Allowed Scopes')),
                DataColumn(label: Text('Active')),
                DataColumn(label: Text('')),
              ],
              entityToRow: (owner) => [
                DataCell(
                  owner.active
                      ? Text(owner.username)
                      : Row(
                          children: [
                            Text(owner.username),
                            const Padding(padding: EdgeInsets.symmetric(horizontal: 4)),
                            const IconButton(
                              tooltip: 'Deactivated resource owner',
                              onPressed: null,
                              icon: Icon(Icons.warning),
                            )
                          ],
                        ),
                ),
                DataCell(Text(owner.subject ?? '-')),
                DataCell(Text('${owner.allowedScopes.join(', ')}')),
                DataCell(Text(owner.active ? 'Yes' : 'No')),
                DataCell(
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      IconButton(
                        tooltip: widget.client.subject == owner.username
                            ? 'Cannot update the current resource owner'
                            : 'Update Resource Owner',
                        onPressed: widget.client.subject == owner.username ? null : () => _editOwner(context, owner),
                        icon: const Icon(Icons.edit),
                      ),
                      IconButton(
                        tooltip: 'Update Resource Owner Credentials',
                        onPressed: () => _editOwnerCredentials(context, owner),
                        icon: const Icon(Icons.lock_reset),
                      ),
                      IconButton(
                        tooltip: widget.client.subject == owner.username
                            ? 'Cannot remove the current resource owner'
                            : 'Remove Resource Owner',
                        onPressed: widget.client.subject == owner.username
                            ? null
                            : () => _removeOwner(context, owner.username),
                        icon: const Icon(Icons.delete),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ],
        );
      },
    );
  }

  void _createOwner(BuildContext context) async {
    final usernameField = formField(
      title: 'Username',
      errorMessage: 'Username cannot be empty',
      controller: TextEditingController(),
    );

    final rawPasswordField = formField(
      title: 'Password',
      secret: true,
      errorMessage: 'Password cannot be empty',
      controller: TextEditingController(),
    );

    final userSaltField = formField(
      title: 'User Salt (optional)',
      secret: true,
      controller: TextEditingController(),
    );

    final allowedScopesField = formField(
      title: 'Allowed Scopes (comma-separated, optional)',
      controller: TextEditingController(),
    );

    final subjectField = formField(
      title: 'Subject (optional)',
      controller: TextEditingController(),
    );

    showDialog(
      context: context,
      builder: (_) {
        return SimpleDialog(
          title: const Text('Create New Resource Owner'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: widget.passwordDerivationConfig.enabled
                  ? [usernameField, rawPasswordField, userSaltField, allowedScopesField, subjectField]
                  : [usernameField, rawPasswordField, allowedScopesField, subjectField],
              submitAction: 'Create',
              onFormSubmitted: () {
                final subject = subjectField.controller!.text.trim();

                final password = rawPasswordField.controller!.text.trim();

                final futureAuthenticationPassword = (userSaltField.controller?.text.trim() ?? '').isNotEmpty
                    ? DerivedPasswords.deriveHashedUserAuthenticationPassword(
                        password: password,
                        saltPrefix: widget.passwordDerivationConfig.saltPrefix,
                        salt: userSaltField.controller!.text.trim(),
                        iterations: widget.passwordDerivationConfig.iterations,
                        derivedKeySize: widget.passwordDerivationConfig.secretSize,
                      )
                    : Future.value(password);

                final messenger = ScaffoldMessenger.of(context);

                futureAuthenticationPassword.then(
                  (authenticationPassword) {
                    final request = CreateOwner(
                      username: usernameField.controller!.text.trim(),
                      rawPassword: authenticationPassword,
                      allowedScopes: allowedScopesField.controller!.text.split(',').map((s) => s.trim()).toList(),
                      subject: subject.isEmpty ? null : subject,
                    );

                    return widget.client.postOwner(request).then((_) {
                      messenger.showSnackBar(const SnackBar(content: Text('Resource owner created...')));
                      setState(() {});
                    }).onError((e, stackTrace) {
                      messenger.showSnackBar(SnackBar(content: Text('Failed to create resource owner: [$e]')));
                    });
                  },
                  onError: (e) {
                    messenger.showSnackBar(SnackBar(content: Text('Failed to generate authentication password: [$e]')));
                  },
                ).whenComplete(() => Navigator.pop(context));
              },
            )
          ],
        );
      },
    );
  }

  void _editOwner(BuildContext context, ResourceOwner existing) {
    final allowedScopesField = formField(
      title: 'Allowed Scopes (comma-separated, optional)',
      controller: TextEditingController(text: existing.allowedScopes.join(',')),
    );

    bool currentActiveState = existing.active;
    final activeField = StateField(
      title: 'State',
      initialState: existing.active,
      onStateUpdated: (updated) => currentActiveState = updated,
    );

    showDialog(
      context: context,
      builder: (context) {
        return SimpleDialog(
          title: Text('Update Resource Owner [${existing.username}]'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [
                allowedScopesField,
                activeField,
              ],
              submitAction: 'Update',
              onFormSubmitted: () {
                final request = UpdateOwner(
                  allowedScopes: allowedScopesField.controller!.text.split(',').map((s) => s.trim()).toList(),
                  active: currentActiveState,
                );

                final messenger = ScaffoldMessenger.of(context);

                widget.client.putOwner(existing.username, request).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Resource owner updated...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to update resource owner: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
            )
          ],
        );
      },
    );
  }

  void _editOwnerCredentials(BuildContext context, ResourceOwner existing) {
    final rawPasswordField = formField(
      title: 'Password',
      secret: true,
      errorMessage: 'Password cannot be empty',
      controller: TextEditingController(),
    );

    final userSaltField = formField(
      title: 'User Salt (optional)',
      secret: true,
      controller: TextEditingController(),
    );

    showDialog(
      context: context,
      builder: (context) {
        return SimpleDialog(
          title: Text('Update Resource Owner Credentials [${existing.username}]'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: (widget.passwordDerivationConfig.enabled && widget.client.subject != existing.username)
                  ? [rawPasswordField, userSaltField]
                  : [rawPasswordField],
              submitAction: 'Update',
              onFormSubmitted: () {
                final password = rawPasswordField.controller!.text.trim();

                final futureAuthenticationPassword = (userSaltField.controller?.text.trim() ?? '').isNotEmpty
                    ? DerivedPasswords.deriveHashedUserAuthenticationPassword(
                        password: password,
                        saltPrefix: widget.passwordDerivationConfig.saltPrefix,
                        salt: userSaltField.controller!.text.trim(),
                        iterations: widget.passwordDerivationConfig.iterations,
                        derivedKeySize: widget.passwordDerivationConfig.secretSize,
                      )
                    : Future.value(password);

                final messenger = ScaffoldMessenger.of(context);

                futureAuthenticationPassword.then(
                  (authenticationPassword) {
                    final request = UpdateOwnerCredentials(rawPassword: authenticationPassword);

                    return widget.client.putOwnerCredentials(existing.username, request).then((_) {
                      messenger.showSnackBar(const SnackBar(content: Text('Resource owner credentials updated...')));
                      setState(() {});
                    }).onError((e, _) {
                      messenger
                          .showSnackBar(SnackBar(content: Text('Failed to update resource owner credentials: [$e]')));
                    });
                  },
                  onError: (e) {
                    messenger.showSnackBar(SnackBar(content: Text('Failed to generate authentication password: [$e]')));
                  },
                ).whenComplete(() => Navigator.pop(context));
              },
            )
          ],
        );
      },
    );
  }

  void _removeOwner(BuildContext context, String id) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Remove resource owner [$id]?'),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(context);
              },
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () {
                final messenger = ScaffoldMessenger.of(context);

                widget.client.deleteOwner(id).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Resource owner removed...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to remove resource owner: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
              child: const Text('Remove'),
            ),
          ],
        );
      },
    );
  }
}
