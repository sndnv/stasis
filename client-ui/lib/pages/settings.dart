import 'dart:io';

import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/config/app_files.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:flutter/material.dart';
import 'package:flutter_json_view/flutter_json_view.dart';

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

        final configFile = files.paths.config.toSplitPath();

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
          child: const Icon(Icons.stop),
        );

        final view = SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.max,
            mainAxisAlignment: MainAxisAlignment.start,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Card(
                child: Column(
                  children: [
                    ListTile(
                      title: SelectionArea(
                        child: Column(
                          mainAxisSize: MainAxisSize.max,
                          mainAxisAlignment: MainAxisAlignment.start,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Tooltip(
                              message: 'Config file name',
                              child: Text(configFile.b),
                            ),
                            Tooltip(
                              message: 'Config file parent directory',
                              child: Text(configFile.a, style: theme.textTheme.bodySmall),
                            ),
                          ],
                        ),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(24.0, 0.0, 24.0, 8.0),
                      child: tree,
                    ),
                  ],
                ),
              )
            ],
          ),
        );

        return Stack(
          children: [
            Align(
              alignment: Alignment.topCenter,
              child: view,
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
}
