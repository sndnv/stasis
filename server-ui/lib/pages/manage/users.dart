import 'package:data_table_2/data_table_2.dart';
import 'package:flutter/material.dart';
import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/model/api/requests/create_user.dart';
import 'package:server_ui/model/api/requests/update_user_limits.dart';
import 'package:server_ui/model/api/requests/update_user_password.dart';
import 'package:server_ui/model/api/requests/update_user_permissions.dart';
import 'package:server_ui/model/api/requests/update_user_state.dart';
import 'package:server_ui/model/users/user.dart';
import 'package:server_ui/pages/default/components.dart';
import 'package:server_ui/pages/manage/components/entity_form.dart';
import 'package:server_ui/pages/manage/components/entity_table.dart';
import 'package:server_ui/pages/manage/components/extensions.dart';
import 'package:server_ui/model/users/permission.dart';
import 'package:server_ui/pages/manage/components/rendering.dart';

class Users extends StatefulWidget {
  const Users({
    super.key,
    required this.client,
    required this.currentUser,
  });

  final UsersApiClient client;
  final User currentUser;

  @override
  State createState() {
    return _UsersState();
  }
}

class _UsersState extends State<Users> {
  @override
  Widget build(BuildContext context) {
    final queryFilter = Uri.base.queryParameters['filter']?.trim() ?? '';

    return buildPage<List<User>>(
      of: () => widget.client.getUsers(),
      builder: (context, users) {
        return EntityTable<User>(
          entities: users,
          actions: [
            IconButton(
              tooltip: 'Create New User',
              onPressed: () => _createUser(),
              icon: const Icon(Icons.add),
            ),
          ],
          appliedFilter: queryFilter,
          filterBy: (entity, filter) {
            final user = entity as User;
            return user.id.contains(filter) ||
                user.salt.contains(filter) ||
                user.permissions.toList().join().contains(filter);
          },
          header: const Text('Users'),
          columns: [
            EntityTableColumn(label: 'ID', sortBy: (e) => (e.id as String).toMinimizedString()),
            EntityTableColumn(label: 'Salt', size: ColumnSize.S),
            EntityTableColumn(label: 'Active', sortBy: (e) => e.active.toString(), size: ColumnSize.S),
            EntityTableColumn(label: 'Permissions'),
            EntityTableColumn(label: 'Limits', size: ColumnSize.S),
            EntityTableColumn(label: 'Updated', sortBy: (e) => e.updated.toString()),
            EntityTableColumn(label: ''),
          ],
          entityToRow: (entity) {
            final user = entity as User;
            final userPermissions = (user.permissions.toList()..sort()).join(', ');

            return [
              DataCell(
                user.active
                    ? user.id.asShortId()
                    : Row(
                        children: [
                          user.id.asShortId(),
                          const IconButton(
                            tooltip: 'Deactivated user',
                            onPressed: null,
                            icon: Icon(Icons.warning),
                          )
                        ],
                      ),
              ),
              DataCell(user.salt.hiddenWithInfo(
                () => showDialog(
                  context: context,
                  builder: (_) => SimpleDialog(
                    title: Text('Salt for [${user.id.toMinimizedString()}]'),
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Padding(
                            padding: const EdgeInsetsDirectional.only(start: 16.0),
                            child: SelectionArea(
                              child: RichText(
                                text: TextSpan(text: user.salt, style: Theme.of(context).textTheme.headlineMedium),
                              ).withCopyButton(copyText: user.salt),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              )),
              DataCell(Text(user.active ? 'Yes' : 'No')),
              DataCell(
                Tooltip(message: userPermissions, child: Text(userPermissions, overflow: TextOverflow.ellipsis)),
              ),
              DataCell(user.limits?.maxStorage.renderFileSize().withInfo(() {
                    final limits = user.limits!;
                    showDialog(
                      context: context,
                      builder: (_) => SimpleDialog(
                        title: Text('Limits for [${user.id.toMinimizedString()}]'),
                        children: [
                          ListTile(
                            title: const Text('Max Devices'),
                            leading: const Icon(Icons.devices),
                            trailing: Text(limits.maxDevices.renderNumber()),
                          ),
                          ListTile(
                            title: const Text('Max Crates'),
                            leading: const Icon(Icons.data_usage),
                            trailing: Text(limits.maxCrates.renderNumber()),
                          ),
                          ListTile(
                            title: const Text('Max Storage'),
                            leading: const Icon(Icons.sd_storage),
                            trailing: Text(limits.maxStorage.renderFileSize()),
                          ),
                          ListTile(
                            title: const Text('Max Storage per Crate'),
                            leading: const Icon(Icons.sd_storage),
                            trailing: Text(limits.maxStoragePerCrate.renderFileSize()),
                          ),
                          ListTile(
                            title: const Text('Min Retention'),
                            leading: const Icon(Icons.lock_clock),
                            trailing: Text(limits.minRetention.render()),
                          ),
                          ListTile(
                            title: const Text('Max Retention'),
                            leading: const Icon(Icons.lock_clock),
                            trailing: Text(limits.maxRetention.render()),
                          ),
                        ],
                      ),
                    );
                  }) ??
                  const Text('none')),
              DataCell(
                Tooltip(
                  message: 'Created: ${user.created.render()}\nUpdated: ${user.updated.render()}',
                  child: Text(user.updated.render(), overflow: TextOverflow.ellipsis),
                ),
              ),
              DataCell(
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: user.id != widget.currentUser.id
                      ? [
                          IconButton(
                            tooltip: 'Update User State',
                            onPressed: () => _updateUserState(user),
                            icon: const Icon(Icons.power_settings_new),
                          ),
                          IconButton(
                            tooltip: 'Update User Limits',
                            onPressed: () => _updateUserLimits(user),
                            icon: const Icon(Icons.data_usage),
                          ),
                          IconButton(
                            tooltip: 'Update User Permissions',
                            onPressed: () => _updateUserPermissions(user),
                            icon: const Icon(Icons.security),
                          ),
                          IconButton(
                            tooltip: 'Update User Password',
                            onPressed: () => _updateUserPassword(user),
                            icon: const Icon(Icons.lock_reset),
                          ),
                          IconButton(
                            tooltip: 'Remove User',
                            onPressed: () => _removeUser(user.id),
                            icon: const Icon(Icons.delete),
                          ),
                        ]
                      : [
                          const IconButton(
                            tooltip: 'No actions available for the current user',
                            onPressed: null,
                            icon: Icon(Icons.do_not_disturb),
                          ),
                        ],
                ),
              ),
            ];
          },
        );
      },
    );
  }

  void _createUser() async {
    final usernameField = formField(
      title: 'Username',
      errorMessage: 'Username cannot be empty',
      controller: TextEditingController(),
    );

    String? password;
    final passwordField = userPasswordField(
      title: 'Password',
      onChange: (updated) => password = updated,
    );

    List<UserPermission>? permissions;
    final permissionsField = userPermissionsField(
      title: 'Permissions',
      onChange: (updated) => permissions = updated,
      errorMessage: 'Permissions are required',
    );

    UserLimits? limits;
    final limitsField = userLimitsField(
      title: 'User Limits',
      onChange: (updated) => limits = updated,
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return SimpleDialog(
          title: const Text('Create New User'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [usernameField, passwordField, permissionsField, limitsField],
              submitAction: 'Create',
              onFormSubmitted: () {
                final request = CreateUser(
                  username: usernameField.controller!.text.trim(),
                  rawPassword: password!,
                  permissions: permissions!.map((e) => e.name).toSet(),
                  limits: limits,
                );

                final messenger = ScaffoldMessenger.of(context);

                widget.client.createUser(request: request).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('User created...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to create user: [$e]')));
                }).whenComplete(() {
                  if (context.mounted) Navigator.pop(context);
                });
              },
            )
          ],
        );
      },
    );
  }

  void _updateUserState(User existing) {
    bool currentActiveState = existing.active;
    final activeField = stateField(
      title: 'State',
      initialState: existing.active,
      onChange: (updated) => currentActiveState = updated,
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return SimpleDialog(
          title: Text('Update State for User [${existing.id.toMinimizedString()}]'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [activeField],
              submitAction: 'Update',
              onFormSubmitted: () {
                final request = UpdateUserState(active: currentActiveState);

                final messenger = ScaffoldMessenger.of(context);

                widget.client.updateUserState(id: existing.id, request: request).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('User updated...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to update user: [$e]')));
                }).whenComplete(() {
                  if (context.mounted) Navigator.pop(context);
                });
              },
            )
          ],
        );
      },
    );
  }

  void _updateUserLimits(User existing) async {
    UserLimits? limits;
    final limitsField = userLimitsField(
      title: 'User Limits',
      onChange: (updated) => limits = updated,
      initialUserLimits: existing.limits,
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return SimpleDialog(
          title: Text('Update Limits for User [${existing.id.toMinimizedString()}]'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [limitsField],
              submitAction: 'Update',
              onFormSubmitted: () {
                final request = UpdateUserLimits(limits: limits);

                final messenger = ScaffoldMessenger.of(context);

                widget.client.updateUserLimits(id: existing.id, request: request).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('User updated...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to update user: [$e]')));
                }).whenComplete(() {
                  if (context.mounted) Navigator.pop(context);
                });
              },
            )
          ],
        );
      },
    );
  }

  void _updateUserPermissions(User existing) async {
    List<UserPermission>? permissions;
    final permissionsField = userPermissionsField(
      title: 'Permissions',
      onChange: (updated) => permissions = updated,
      errorMessage: 'Permissions are required',
      initialPermissions: existing.permissions.map((e) => UserPermission.fromName(e)).toList(),
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return SimpleDialog(
          title: Text('Update Permissions for User [${existing.id.toMinimizedString()}]'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [permissionsField],
              submitAction: 'Update',
              onFormSubmitted: () {
                final request = UpdateUserPermissions(permissions: permissions!.map((e) => e.name).toSet());

                final messenger = ScaffoldMessenger.of(context);

                widget.client.updateUserPermissions(id: existing.id, request: request).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('User updated...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to update user: [$e]')));
                }).whenComplete(() {
                  if (context.mounted) Navigator.pop(context);
                });
              },
            )
          ],
        );
      },
    );
  }

  void _updateUserPassword(User existing) async {
    String? password;
    final passwordField = userPasswordField(
      title: 'Password',
      onChange: (updated) => password = updated,
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return SimpleDialog(
          title: Text('Update Password for User [${existing.id.toMinimizedString()}]'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [passwordField],
              submitAction: 'Update',
              onFormSubmitted: () {
                final request = UpdateUserPassword(rawPassword: password!);

                final messenger = ScaffoldMessenger.of(context);

                widget.client.updateUserPassword(id: existing.id, request: request).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('User updated...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to update user: [$e]')));
                }).whenComplete(() {
                  if (context.mounted) Navigator.pop(context);
                });
              },
            )
          ],
        );
      },
    );
  }

  void _removeUser(String id) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Remove user [${id.toMinimizedString()}]?'),
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

                widget.client.deleteUser(id: id).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('User removed...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to remove user: [$e]')));
                }).whenComplete(() {
                  if (context.mounted) Navigator.pop(context);
                });
              },
              child: const Text('Remove'),
            ),
          ],
        );
      },
    );
  }
}
