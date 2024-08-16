import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_json_view/flutter_json_view.dart';
import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/config/app_files.dart';
import 'package:stasis_client_ui/model/api/requests/update_user_password.dart';
import 'package:stasis_client_ui/model/api/requests/update_user_salt.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/sizing.dart';
import 'package:stasis_client_ui/pages/components/update_user_credentials_form.dart';

class Settings extends StatelessWidget {
  const Settings({
    super.key,
    required this.files,
    required this.client,
  });

  final AppFiles files;
  final ClientApi client;

  @override
  Widget build(BuildContext context) {
    return buildPage<JsonView>(
      of: () => Future.delayed(
        const Duration(milliseconds: 200),
        () {
          if (!context.mounted) return JsonView.map(const {});

          final theme = Theme.of(context);
          final mainStyle = theme.textTheme.bodyMedium!;

          return JsonView.map(
            files.config.sanitized().raw,
            theme: JsonViewTheme(
              backgroundColor: theme.canvasColor,
              defaultTextStyle: mainStyle,
              closeIcon: Icon(Icons.arrow_drop_up, size: 18, color: theme.colorScheme.secondary),
              openIcon: Icon(Icons.arrow_drop_down, size: 18, color: theme.colorScheme.secondary),
              stringStyle: mainStyle.copyWith(color: theme.primaryColor),
            ),
          );
        },
      ),
      builder: (context, tree) {
        final theme = Theme.of(context);

        final stopBackgroundService = FloatingActionButton.small(
          heroTag: null,
          onPressed: () {
            confirmationDialog(
              context,
              title: 'Stop background service?',
              content: Text(
                'Stopping the background service will terminate all active operations!',
                style: theme.textTheme.bodySmall,
              ),
              onConfirm: () {
                final messenger = ScaffoldMessenger.of(context);
                client.stop().then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Background service stopped...')));
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to stop background service: [$e]')));
                }).then((_) => Future.delayed(const Duration(seconds: 1), () => exit(0)));
              },
            );
          },
          tooltip: 'Stop background service',
          child: Icon(Icons.stop, color: theme.colorScheme.error),
        );

        return Stack(
          children: [
            Align(
              alignment: Alignment.topCenter,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.max,
                  mainAxisAlignment: MainAxisAlignment.start,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _buildCredentialsCard(context),
                    _buildConfigCard(context, tree),
                  ],
                ),
              ),
            ),
            Align(
              alignment: Alignment.bottomRight,
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: stopBackgroundService,
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _buildConfigCard(BuildContext context, JsonView tree) {
    final theme = Theme.of(context);

    final configFile = files.paths.config;

    return Card(
      child: Column(
        children: [
          ExpansionTile(
            title: Column(
              mainAxisSize: MainAxisSize.max,
              mainAxisAlignment: MainAxisAlignment.start,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Row(
                  children: [
                    Icon(Icons.settings),
                    Padding(padding: EdgeInsets.only(right: 4.0)),
                    Text('Configuration'),
                  ],
                ),
                Tooltip(
                  message: 'Config file path',
                  child: Text(configFile, style: theme.textTheme.bodySmall),
                ),
              ],
            ),
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(24.0, 0.0, 24.0, 8.0),
                child: tree,
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildCredentialsCard(BuildContext context) {
    final theme = Theme.of(context);
    final media = MediaQuery.of(context);

    final updateUserPasswordContainer = createBasicCard(
      theme,
      [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Update User Password', style: theme.textTheme.titleSmall),
            UpdateUserCredentialsForm(
              title: 'Password',
              icon: Icons.lock,
              isSecret: true,
              action: (currentPassword, newPassword) async {
                final request = UpdateUserPassword(currentPassword: currentPassword, newPassword: newPassword);
                return client.updateOwnPassword(request: request);
              },
            ),
          ],
        ),
      ],
    );

    final updateUserSaltContainer = createBasicCard(
      theme,
      [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Update User Salt', style: theme.textTheme.titleSmall),
            UpdateUserCredentialsForm(
              title: 'Salt',
              icon: Icons.security,
              isSecret: false,
              action: (currentPassword, newSalt) async {
                final request = UpdateUserSalt(currentPassword: currentPassword, newSalt: newSalt);
                return client.updateOwnSalt(request: request);
              },
            ),
          ],
        ),
      ],
    );

    return Card(
      child: Column(
        children: [
          ExpansionTile(
            title: Column(
              mainAxisSize: MainAxisSize.max,
              mainAxisAlignment: MainAxisAlignment.start,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Row(
                  children: [
                    Icon(Icons.lock),
                    Padding(padding: EdgeInsets.only(right: 4.0)),
                    Text('Manage Credentials'),
                  ],
                ),
                Text('Update current user password and salt value', style: theme.textTheme.bodySmall),
              ],
            ),
            children: [
              Column(
                mainAxisSize: MainAxisSize.max,
                children: media.size.width > Sizing.sm * 1.1
                    ? [
                        Row(
                          mainAxisSize: MainAxisSize.max,
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.center,
                          children: [
                            SizedBox(width: Sizing.sm * 0.5, child: updateUserPasswordContainer),
                            SizedBox(width: Sizing.sm * 0.5, child: updateUserSaltContainer),
                          ],
                        )
                      ]
                    : [updateUserPasswordContainer, updateUserSaltContainer],
              )
            ],
          ),
        ],
      ),
    );
  }
}
