import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/api/requests/create_dataset_definition.dart';
import 'package:stasis_client_ui/model/datasets/dataset_definition.dart';
import 'package:stasis_client_ui/model/devices/device.dart';
import 'package:stasis_client_ui/pages/backup_entries.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/dataset_definition_summary.dart';
import 'package:stasis_client_ui/pages/components/entity_form.dart';
import 'package:stasis_client_ui/pages/components/sizing.dart';
import 'package:stasis_client_ui/pages/components/top_bar.dart';
import 'package:stasis_client_ui/utils/pair.dart';
import 'package:flutter/material.dart';

class Backup extends StatefulWidget {
  const Backup({
    super.key,
    required this.client,
  });

  final ClientApi client;

  @override
  State createState() {
    return _BackupState();
  }
}

class _BackupState extends State<Backup> {
  @override
  Widget build(BuildContext context) {
    return buildPage<Pair<List<DatasetDefinition>, Device>>(
      of: () => widget.client
          .getDatasetDefinitions()
          .then((definitions) => widget.client.getCurrentDevice().then((device) => Pair(definitions, device))),
      builder: (context, data) {
        final theme = Theme.of(context);
        final media = MediaQuery.of(context);

        final definitions = data.a;
        final currentDevice = data.b;

        final definitionsList = ListView(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          children: definitions.map((d) {
            return DatasetDefinitionSummary.build(
              context,
              definition: d,
              client: widget.client,
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                    builder: (_) => Scaffold(
                      appBar: TopBar.fromTitle(context, 'Backup definition details'),
                      body: BackupEntries(definition: d, client: widget.client),
                    ),
                    fullscreenDialog: true,
                  ),
                );
              },
            );
          }).toList(),
        );

        final noDefinitions = Padding(
          padding: const EdgeInsets.all(16.0),
          child: Center(
            child: Text(
              'No definitions',
              style: theme.textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic),
            ),
          ),
        );

        final addDefinitionButton = media.size.width > Sizing.xs
            ? FloatingActionButton.extended(
                heroTag: null,
                onPressed: () => _createDatasetDefinition(currentDevice),
                icon: const Icon(Icons.add),
                label: const Text('ADD DEFINITION'),
              )
            : Tooltip(
                message: 'Add definition',
                child: FloatingActionButton.small(
                  heroTag: null,
                  onPressed: () => _createDatasetDefinition(currentDevice),
                  child: const Icon(Icons.add),
                ),
              );

        final content = Card(
          margin: const EdgeInsets.all(16.0),
          child: Column(
            mainAxisSize: MainAxisSize.max,
            mainAxisAlignment: MainAxisAlignment.start,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [definitions.isNotEmpty ? definitionsList : noDefinitions],
          ),
        );

        return Stack(
          children: [
            Align(
              alignment: Alignment.topCenter,
              child: boxed(context, child: content),
            ),
            Align(
              alignment: Alignment.bottomCenter,
              child: Padding(
                padding: const EdgeInsets.only(bottom: 16.0),
                child: addDefinitionButton,
              ),
            ),
          ],
        );
      },
    );
  }

  void _createDatasetDefinition(Device currentDevice) {
    final infoField = formField(
      title: 'Info',
      errorMessage: 'Info cannot be empty',
      controller: TextEditingController(),
    );

    final redundantCopiesField = formField(
      title: 'Redundant Copies',
      errorMessage: 'Redundant copies must be provided',
      controller: TextEditingController(),
      type: TextInputType.number,
    );

    Retention? existingVersions;
    final existingVersionsField = retentionField(
      title: 'Existing Versions',
      onChange: (updated) => existingVersions = updated,
    );

    Retention? removedVersions;
    final removedVersionsField = retentionField(
      title: 'Removed Versions',
      onChange: (updated) => removedVersions = updated,
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return SimpleDialog(
          title: const Text('Create New Dataset Definition'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [infoField, redundantCopiesField, existingVersionsField, removedVersionsField],
              submitAction: 'Create',
              onFormSubmitted: () {
                final request = CreateDatasetDefinition(
                  info: infoField.controller!.text.trim(),
                  device: currentDevice.id,
                  redundantCopies: int.parse(redundantCopiesField.controller!.text.trim()),
                  existingVersions: existingVersions!,
                  removedVersions: removedVersions!,
                );

                final messenger = ScaffoldMessenger.of(context);

                widget.client.defineBackup(request: request).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Dataset definition created...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to create dataset definition: [$e]')));
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
}
