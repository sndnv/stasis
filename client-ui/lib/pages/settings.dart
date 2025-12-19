import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_json_view/flutter_json_view.dart';
import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/config/app_files.dart';
import 'package:stasis_client_ui/model/analytics/analytics_state.dart';
import 'package:stasis_client_ui/model/api/requests/update_user_password.dart';
import 'package:stasis_client_ui/model/api/requests/update_user_salt.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/client_logs.dart';
import 'package:stasis_client_ui/pages/components/update_user_credentials_form.dart';
import 'package:stasis_client_ui/utils/debouncer.dart';

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
                client
                    .stop()
                    .then((_) {
                      messenger.showSnackBar(const SnackBar(content: Text('Background service stopped...')));
                    })
                    .onError((e, stackTrace) {
                      messenger.showSnackBar(SnackBar(content: Text('Failed to stop background service: [$e]')));
                    })
                    .then((_) => Future.delayed(const Duration(seconds: 1), () => exit(0)));
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
                    _buildAnalyticsCard(context, client),
                    _buildLogsCard(context),
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

    final updateUserPasswordTile = ListTile(
      leading: Icon(Icons.password),
      title: Text('Update User Password'),
      onTap: () {
        showDialog(
          context: context,
          builder: (context) {
            return _renderCredentialsUpdateDialog(
              title: const Text('Update User Password'),
              content: UpdateUserCredentialsForm(
                title: 'Password',
                icon: Icons.lock,
                isSecret: true,
                action: (currentPassword, newPassword) async {
                  final request = UpdateUserPassword(
                    currentPassword: currentPassword,
                    newPassword: newPassword,
                  );
                  return client.updateOwnPassword(request: request);
                },
              ),
            );
          },
        );
      },
    );

    final updateUserSaltTile = ListTile(
      leading: Icon(Icons.security),
      title: Text('Update User Salt'),
      onTap: () {
        showDialog(
          context: context,
          builder: (context) {
            return _renderCredentialsUpdateDialog(
              title: const Text('Update User Salt'),
              content: UpdateUserCredentialsForm(
                title: 'Salt',
                icon: Icons.security,
                isSecret: false,
                action: (currentPassword, newSalt) async {
                  final request = UpdateUserSalt(currentPassword: currentPassword, newSalt: newSalt);
                  return client.updateOwnSalt(request: request);
                },
              ),
            );
          },
        );
      },
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
                    Text('Manage credentials'),
                  ],
                ),
                Text('Update current user password and salt value', style: theme.textTheme.bodySmall),
              ],
            ),
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(24.0, 0.0, 24.0, 8.0),
                child: ListView(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  children: [updateUserPasswordTile, updateUserSaltTile],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildAnalyticsCard(BuildContext context, ClientApi client) {
    return buildPage<AnalyticsState>(
      of: client.getAnalyticsState,
      builder: (context, data) {
        final theme = Theme.of(context);

        final analyticsEnabled = data.entry.runtime.app != 'none;none;0';

        final encodedData = json.encode(data);

        final info = Column(
          children: [
            infoSection(
              content: Text(
                'The following information is collected in order to detect and resolve issues with client applications. '
                'This data is generic (for example, how many backups were started or how many schedules were executed) '
                'and it does not include user- or device-identifiable information.',
                style: theme.textTheme.bodySmall,
              ),
              color: theme.primaryColor,
              padding: const EdgeInsets.symmetric(vertical: 8.0, horizontal: 24.0),
            ),
            infoSection(
              content: Text(
                'The behaviour of this feature can be controlled via the configuration file (above); it is possible '
                'to adjust what is collected, how frequently information is persisted locally and sent remotely, and '
                'collection can be turned off entirely.',
                style: theme.textTheme.bodySmall,
              ),
              color: theme.primaryColor,
              padding: const EdgeInsets.symmetric(vertical: 8.0, horizontal: 24.0),
            ),
          ],
        );

        final sendDebouncer = Debouncer(timeout: Duration(milliseconds: 500));

        final sendButton = IconButton(
          splashRadius: 16.0,
          iconSize: 16.0,
          tooltip: 'Send analytics data to the server',
          onPressed: () {
            sendDebouncer.run(() {
              final messenger = ScaffoldMessenger.of(context);
              client
                  .sendAnalyticsState()
                  .then((_) {
                    messenger.showSnackBar(const SnackBar(content: Text('Request sent...')));
                  })
                  .onError((e, stackTrace) {
                    messenger.showSnackBar(SnackBar(content: Text('Failed to send analytics data: [$e]')));
                  });
            });
          },
          icon: const Icon(Icons.upload),
        );

        final copyButton = IconButton(
          splashRadius: 16.0,
          iconSize: 16.0,
          tooltip: 'Copy analytics data',
          onPressed: () {
            Clipboard.setData(ClipboardData(text: encodedData));
            ScaffoldMessenger.of(
              context,
            ).showSnackBar(const SnackBar(content: Text('Analytics data copied to clipboard...')));
          },
          icon: const Icon(Icons.copy),
        );

        return Card(
          child: Column(
            children: [
              ExpansionTile(
                enabled: analyticsEnabled,
                title: Row(
                  mainAxisSize: MainAxisSize.max,
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      mainAxisSize: MainAxisSize.max,
                      mainAxisAlignment: MainAxisAlignment.start,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Icon(Icons.analytics),
                            Padding(padding: EdgeInsets.only(right: 4.0)),
                            Text('Analytics'),
                          ],
                        ),
                        Text(
                          analyticsEnabled ? 'Show collected analytics data' : 'Analytics collection is disabled',
                          style: theme.textTheme.bodySmall,
                        ),
                      ],
                    ),
                    Row(
                      children: [
                        copyButton,
                        sendButton,
                      ],
                    ),
                  ],
                ),
                children: analyticsEnabled
                    ? [
                        info,
                        Padding(
                          padding: const EdgeInsets.fromLTRB(24.0, 0.0, 24.0, 8.0),
                          child: _renderAnalyticsState(context, encodedData),
                        ),
                      ]
                    : [],
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildLogsCard(BuildContext context) {
    final theme = Theme.of(context);

    return Card(
      child: Column(
        children: [
          ListTile(
            title: Row(
              mainAxisSize: MainAxisSize.max,
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  mainAxisSize: MainAxisSize.max,
                  mainAxisAlignment: MainAxisAlignment.start,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.terminal),
                        Padding(padding: EdgeInsets.only(right: 4.0)),
                        Text('View client logs'),
                      ],
                    ),
                    Text('Show the latest client logs', style: theme.textTheme.bodySmall),
                  ],
                ),
              ],
            ),
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
  }

  Widget _renderAnalyticsState(BuildContext context, String data) {
    final theme = Theme.of(context);
    final mainStyle = theme.textTheme.bodyMedium!;

    return JsonView.string(
      data,
      theme: JsonViewTheme(
        backgroundColor: theme.canvasColor,
        defaultTextStyle: mainStyle,
        closeIcon: Icon(Icons.arrow_drop_up, size: 18, color: theme.colorScheme.secondary),
        openIcon: Icon(Icons.arrow_drop_down, size: 18, color: theme.colorScheme.secondary),
        stringStyle: mainStyle.copyWith(color: theme.primaryColor),
      ),
    );
  }

  Widget _renderCredentialsUpdateDialog({required Widget title, required Widget content}) {
    return SimpleDialog(
      title: title,
      children: [
        SizedBox(
          width: 480,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.all(8.0),
                child: content,
              ),
            ],
          ),
        ),
      ],
    );
  }
}
