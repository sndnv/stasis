import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/model/api/requests/update_user_password.dart';
import 'package:server_ui/model/datasets/dataset_definition.dart';
import 'package:server_ui/model/devices/device.dart';
import 'package:server_ui/model/users/user.dart';
import 'package:server_ui/api/derived_passwords.dart';
import 'package:server_ui/pages/default/components.dart';
import 'package:server_ui/pages/manage/components/entity_form.dart';
import 'package:server_ui/pages/manage/components/extensions.dart';
import 'package:server_ui/pages/manage/components/rendering.dart';
import 'package:server_ui/utils/pair.dart';
import 'package:server_ui/utils/triple.dart';
import 'package:server_ui/pages/page_destinations.dart';

class Home extends StatelessWidget {
  const Home({
    super.key,
    required this.currentUser,
    required this.usersClient,
    required this.devicesClient,
    required this.definitionsClient,
    required this.passwordDerivationConfig,
  });

  final User currentUser;
  final UsersApiClient usersClient;
  final DevicesApiClient devicesClient;
  final DatasetDefinitionsApiClient definitionsClient;
  final UserAuthenticationPasswordDerivationConfig passwordDerivationConfig;

  @override
  Widget build(BuildContext context) {
    return buildPage<Pair<List<Device>, List<DatasetDefinition>>>(
      of: () => _loadData(),
      builder: (context, data) {
        final devices = (data.a..sort((a, b) => a.name.compareTo(b.name))).where((device) => device.active);
        final definitions = (data.b..sort((a, b) => a.info.compareTo(b.info)));

        final activeDevicesInfo = ListView(
          shrinkWrap: true,
          children: devices
              .map(
                (device) => ListTile(
                  title: device.name.withLink(
                    Link(
                      buildContext: context,
                      destination: PageRouterDestination.devices,
                      withFilter: device.id,
                    ),
                  ),
                  leading: Icon(PageRouterDestination.devices.icon),
                ),
              )
              .toList(),
        );

        final definitionsInfo = ListView(
          shrinkWrap: true,
          children: definitions
              .map(
                (definition) => ListTile(
                  title: definition.info.withLink(
                    Link(
                      buildContext: context,
                      destination: PageRouterDestination.definitions,
                      withFilter: definition.id,
                    ),
                  ),
                  leading: Icon(PageRouterDestination.definitions.icon),
                ),
              )
              .toList(),
        );

        final summary = Card(
          child: Column(
            children: [
              ListTile(
                leading: const Icon(Icons.settings_outlined),
                title: const Text('Summary'),
                subtitle: Text(
                  'Dataset Definitions (${definitions.length}), Active Devices (${devices.length})',
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [Expanded(child: definitionsInfo), Expanded(child: activeDevicesInfo)],
                ),
              ),
            ],
          ),
        );

        final userDetails = Card(
          child: Column(
            children: [
              ListTile(
                leading: const Icon(Icons.person),
                title: Text(currentUser.id.toMinimizedString()),
                subtitle: const Text('Current User'),
              ),
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: ListView(
                  shrinkWrap: true,
                  children: [
                    ListTile(
                      title: const Padding(padding: EdgeInsets.all(4.0), child: Text('Permissions')),
                      leading: const Icon(Icons.security),
                      subtitle: Wrap(
                        spacing: 4.0,
                        runSpacing: 4.0,
                        children: (currentUser.permissions.toList()..sort()).map((e) => Chip(label: Text(e))).toList(),
                      ),
                    ),
                    ListTile(
                      title: const Text('Limits'),
                      leading: const Icon(Icons.data_usage),
                      subtitle: Wrap(
                        spacing: 4.0,
                        runSpacing: 4.0,
                        children: currentUser.limits != null
                            ? <Triple<Icon, String, String>>[
                                Triple(
                                  const Icon(Icons.devices),
                                  'Devices',
                                  currentUser.limits!.maxDevices.toString(),
                                ),
                                Triple(
                                  const Icon(Icons.data_usage),
                                  'Crates',
                                  currentUser.limits!.maxCrates.renderNumber(),
                                ),
                                Triple(
                                  const Icon(Icons.sd_storage),
                                  'Storage',
                                  currentUser.limits!.maxStorage.renderFileSize(),
                                ),
                                Triple(
                                  const Icon(Icons.sd_storage),
                                  'Storage per Crate',
                                  currentUser.limits!.maxStoragePerCrate.renderFileSize(),
                                ),
                                Triple(
                                  const Icon(Icons.lock_clock),
                                  'Retention',
                                  '${currentUser.limits!.minRetention.render()} to ${currentUser.limits!.maxRetention.render()}',
                                ),
                              ]
                                .map(
                                  (e) => Chip(
                                    avatar: e.a,
                                    label: Text('${e.b}: ${e.c}'),
                                    padding: const EdgeInsets.fromLTRB(8.0, 4.0, 4.0, 4.0),
                                  ),
                                )
                                .toList()
                            : [const Text('none')],
                      ),
                    ),
                  ],
                ),
              ),
              ButtonBar(
                alignment: MainAxisAlignment.end,
                children: [
                  IconButton(
                    tooltip: 'Copy User ID',
                    onPressed: () async => await Clipboard.setData(ClipboardData(text: currentUser.id)),
                    icon: const Icon(Icons.copy),
                  ),
                  IconButton(
                    tooltip: 'Reset Password',
                    onPressed: () => _updateUserPassword(context),
                    icon: const Icon(Icons.lock_reset),
                  ),
                  IconButton(
                    tooltip: 'Deactivate Account',
                    onPressed: () => _deactivateUser(context),
                    icon: const Icon(Icons.do_not_disturb),
                  ),
                ],
              ),
            ],
          ),
        );

        return Column(
          mainAxisSize: MainAxisSize.max,
          mainAxisAlignment: MainAxisAlignment.start,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Padding(padding: const EdgeInsets.fromLTRB(16.0, 16.0, 8.0, 16.0), child: summary),
                ),
                Expanded(
                  child: Padding(padding: const EdgeInsets.fromLTRB(8.0, 16.0, 16.0, 16.0), child: userDetails),
                ),
              ],
            )
          ],
        );
      },
    );
  }

  Future<Pair<List<Device>, List<DatasetDefinition>>> _loadData() async {
    return await devicesClient.getDevices(privileged: false).then(
          (devices) => definitionsClient.getDatasetDefinitions(privileged: false).then(
                (definitions) => Pair(devices, definitions),
              ),
        );
  }

  void _updateUserPassword(BuildContext context) async {
    String? password;
    final passwordField = userPasswordField(
      title: 'Password',
      onChange: (updated) => password = updated,
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) {
        return SimpleDialog(
          title: const Text('Reset Password'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [passwordField],
              submitAction: 'Update',
              onFormSubmitted: () {
                final messenger = ScaffoldMessenger.of(context);

                final rawPassword = password!;

                usersClient.resetOwnSalt().then((response) {
                  final futureAuthenticationPassword = passwordDerivationConfig.enabled
                      ? DerivedPasswords.deriveHashedUserAuthenticationPassword(
                          password: rawPassword,
                          saltPrefix: passwordDerivationConfig.saltPrefix,
                          salt: response.salt,
                          iterations: passwordDerivationConfig.iterations,
                          derivedKeySize: passwordDerivationConfig.secretSize,
                        )
                      : Future.value(rawPassword);

                  return futureAuthenticationPassword
                      .then(
                        (authenticationPassword) => usersClient.updateOwnPassword(
                          request: UpdateUserPassword(rawPassword: authenticationPassword),
                        ),
                      )
                      .then(
                        (_) => passwordDerivationConfig.enabled
                            ? _showUpdatedSalt(context, response.salt)
                            : Future.value(),
                      );
                }).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('User password updated...')));
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to update user password: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
            )
          ],
        );
      },
    );
  }

  void _deactivateUser(BuildContext context) {
    final theme = Theme.of(context);

    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('Deactivate Account?'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              style: ButtonStyle(
                backgroundColor: WidgetStateColor.resolveWith((states) => theme.buttonTheme.colorScheme!.error),
              ),
              onPressed: () {
                final messenger = ScaffoldMessenger.of(context);

                usersClient.deactivateSelf().then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('User deactivated...')));
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to deactivate user: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
              child: const Text('Deactivate'),
            ),
          ],
        );
      },
    );
  }

  Future<void> _showUpdatedSalt(BuildContext context, String salt) {
    return showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return AlertDialog(
          title: const Text('Updated Salt'),
          content: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Padding(
                padding: const EdgeInsetsDirectional.only(start: 16.0),
                child: SelectionArea(
                  child: RichText(
                    text: TextSpan(text: salt, style: Theme.of(context).textTheme.headlineMedium),
                  ).withCopyButton(copyText: salt),
                ),
              ),
            ],
          ),
          actions: [
            ElevatedButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Close'),
            )
          ],
        );
      },
    );
  }
}
