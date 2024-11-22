import 'package:collection/collection.dart';
import 'package:flutter/material.dart';
import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/datasets/dataset_definition.dart';
import 'package:stasis_client_ui/model/datasets/dataset_entry.dart';
import 'package:stasis_client_ui/model/datasets/dataset_metadata.dart';
import 'package:stasis_client_ui/model/operations/operation.dart' as operation;
import 'package:stasis_client_ui/model/operations/operation_progress.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/extensions.dart';
import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:stasis_client_ui/pages/components/sizing.dart';
import 'package:stasis_client_ui/utils/pair.dart';
import 'package:stasis_client_ui/utils/triple.dart';

class Home extends StatelessWidget {
  const Home({
    super.key,
    required this.client,
  });

  final ClientApi client;

  @override
  Widget build(BuildContext context) {
    return buildPage<Triple<Pair<DatasetEntry, DatasetMetadata>?, OperationProgress?, DatasetDefinition?>>(
      of: () => _loadData(),
      builder: (context, data) {
        final theme = Theme.of(context);
        final media = MediaQuery.of(context);

        final backup = _renderLatestBackup(theme, data.a);
        final operation = _renderLatestOperation(theme, data.b);
        final defaultDefinition = data.c;

        const padding = EdgeInsets.fromLTRB(8.0, 16.0, 8.0, 16.0);

        const minCardHeight = 128.0;

        final backupContainer = Padding(
          padding: padding,
          child: Column(
            mainAxisSize: MainAxisSize.max,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Last Backup', style: theme.textTheme.bodySmall),
              ConstrainedBox(
                constraints: const BoxConstraints(minHeight: minCardHeight),
                child: createBasicCard(theme, [backup]),
              ),
            ],
          ),
        );

        final operationContainer = Padding(
          padding: padding,
          child: Column(
            mainAxisSize: MainAxisSize.max,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Last Operation', style: theme.textTheme.bodySmall),
              ConstrainedBox(
                constraints: const BoxConstraints(minHeight: minCardHeight),
                child: createBasicCard(theme, [operation]),
              ),
            ],
          ),
        );

        Widget? startBackupButton;
        if (defaultDefinition != null) {
          startBackupButton = Tooltip(
            message: 'Start a backup with the default definition',
            child: FloatingActionButton.small(
              heroTag: null,
              onPressed: () {
                final messenger = ScaffoldMessenger.of(context);
                client.startBackup(definition: defaultDefinition.id).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Backup started...')));
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to start backup: [$e]')));
                });
              },
              child: const Icon(Icons.upload),
            ),
          );
        }

        return Stack(
          children: [
            Align(
              alignment: Alignment.topCenter,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.max,
                  mainAxisAlignment: MainAxisAlignment.start,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: media.size.width > Sizing.sm
                      ? [
                          Row(
                            mainAxisSize: MainAxisSize.max,
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [Expanded(child: backupContainer), Expanded(child: operationContainer)],
                          )
                        ]
                      : [backupContainer, operationContainer],
                ),
              ),
            ),
            Align(
              alignment: Alignment.bottomRight,
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Row(
                  mainAxisSize: MainAxisSize.max,
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [startBackupButton].whereNotNull().toList(),
                ),
              ),
            ),
          ],
        );
      },
    );
  }

  Future<Triple<Pair<DatasetEntry, DatasetMetadata>?, OperationProgress?, DatasetDefinition?>> _loadData() async {
    return await _getLatestOperation().then(
      (operation) => _getLatestMetadata().then(
        (metadata) => _getDefaultDatasetDefinition().then(
          (definition) => Triple(metadata, operation, definition),
        ),
      ),
    );
  }

  Future<Pair<DatasetEntry, DatasetMetadata>?> _getLatestMetadata() async {
    final definitions = await client.getDatasetDefinitions();

    final entries = await Stream.fromIterable(definitions)
        .asyncMap((definition) => client.getLatestDatasetEntryForDefinition(definition: definition.id))
        .expand<DatasetEntry>((entry) => (entry != null) ? [entry] : [])
        .toList()
      ..sort((a, b) => b.created.compareTo(a.created));

    final latest = entries.firstOrNull;

    return latest != null
        ? await client.getDatasetMetadata(entry: latest.id).then((metadata) => Pair(latest, metadata))
        : null;
  }

  Future<OperationProgress?> _getLatestOperation() async {
    final completed = (await client.getOperations(state: operation.State.completed))
        .where((o) => o.progress.completed != null)
        .toList()
      ..sort((a, b) => b.progress.completed?.compareTo(a.progress.completed!) ?? 0);

    return completed.firstOrNull;
  }

  Future<DatasetDefinition?> _getDefaultDatasetDefinition() async {
    final definitions = (await client.getDatasetDefinitions())..sort((a, b) => a.created.compareTo(b.created));

    return definitions.firstOrNull;
  }

  Widget _renderLatestBackup(ThemeData theme, Pair<DatasetEntry, DatasetMetadata>? data) {
    final mediumBold = theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold);
    final mediumItalic = theme.textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic);

    if (data != null) {
      final entry = data.a;
      final metadata = data.b;

      final title = RichText(
        text: TextSpan(
          children: [
            TextSpan(text: entry.created.renderAsDate(), style: mediumBold),
            TextSpan(text: ', ', style: theme.textTheme.bodyMedium),
            TextSpan(text: entry.created.renderAsTime(), style: mediumBold),
            TextSpan(text: ' (', style: theme.textTheme.bodyMedium),
            TextSpan(text: entry.id.toMinimizedString(), style: mediumItalic),
            TextSpan(text: ')', style: theme.textTheme.bodyMedium),
          ],
        ),
      );

      final subtitle = RichText(
        text: TextSpan(
          children: [
            TextSpan(text: 'Crates: ', style: theme.textTheme.bodyMedium),
            TextSpan(text: entry.data.length.toString(), style: mediumBold),
            TextSpan(text: ', Changes: ', style: theme.textTheme.bodyMedium),
            TextSpan(
              text: (metadata.contentChanged.length + metadata.metadataChanged.length).toString(),
              style: mediumBold,
            ),
            TextSpan(text: ', Size: ', style: theme.textTheme.bodyMedium),
            TextSpan(text: metadata.contentChangedBytes.renderFileSize(), style: mediumBold),
            TextSpan(text: '\n', style: theme.textTheme.bodyMedium),
          ],
        ),
      );

      return ListTile(
        title: title,
        subtitle: Padding(padding: const EdgeInsets.symmetric(horizontal: 4.0), child: subtitle),
      );
    } else {
      return ListTile(title: Text('No data', style: mediumItalic, textAlign: TextAlign.center));
    }
  }

  Widget _renderLatestOperation(ThemeData theme, OperationProgress? operation) {
    final mediumBold = theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold);
    final mediumItalic = theme.textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic);

    if (operation != null) {
      final title = RichText(
        text: TextSpan(
          children: [
            TextSpan(text: operation.type.render(), style: mediumBold),
            TextSpan(text: ' (', style: theme.textTheme.bodyMedium),
            TextSpan(text: operation.operation.toMinimizedString(), style: mediumItalic),
            TextSpan(text: ')', style: theme.textTheme.bodyMedium),
          ],
        ),
      );

      final subtitle = RichText(
        text: TextSpan(
          children: [
                TextSpan(text: 'Started: ', style: theme.textTheme.bodyMedium),
                TextSpan(text: operation.progress.started.render(), style: mediumBold),
              ] +
              [
                TextSpan(text: '\nProcessed: ', style: theme.textTheme.bodyMedium),
                TextSpan(text: operation.progress.processed.toString(), style: mediumBold),
                TextSpan(text: ' of ', style: theme.textTheme.bodyMedium),
                TextSpan(text: operation.progress.total.toString(), style: mediumBold),
              ] +
              (operation.progress.failures > 0
                  ? [
                      TextSpan(text: ', Errors: ', style: theme.textTheme.bodyMedium),
                      TextSpan(text: operation.progress.failures.toString(), style: mediumBold),
                    ]
                  : []) +
              [
                TextSpan(text: '\nCompleted: ', style: theme.textTheme.bodyMedium),
                TextSpan(text: operation.progress.completed!.render(), style: mediumBold),
              ],
        ),
      );

      return ListTile(
        title: title,
        subtitle: Padding(padding: const EdgeInsets.symmetric(horizontal: 4.0), child: subtitle),
      );
    } else {
      return ListTile(title: Text('No data', style: mediumItalic, textAlign: TextAlign.center));
    }
  }
}
