import 'package:flutter/material.dart';
import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/api/requests/create_dataset_definition.dart';
import 'package:stasis_client_ui/model/api/requests/update_dataset_definition.dart';
import 'package:stasis_client_ui/model/datasets/dataset_definition.dart';
import 'package:stasis_client_ui/model/devices/device.dart';
import 'package:stasis_client_ui/pages/backup_entries.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/context/context_menu.dart';
import 'package:stasis_client_ui/pages/components/context/entry_action.dart';
import 'package:stasis_client_ui/pages/components/dataset_definition_summary.dart';
import 'package:stasis_client_ui/pages/components/entity_form.dart';
import 'package:stasis_client_ui/pages/components/top_bar.dart';
import 'package:stasis_client_ui/utils/pair.dart';

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

        final definitions = data.a;
        final currentDevice = data.b;

        void show(DatasetDefinition definition) {
          Navigator.push(
            context,
            MaterialPageRoute<void>(
              builder: (_) => Scaffold(
                appBar: TopBar.fromTitle(context, 'Backup definition details'),
                body: BackupEntries(definition: definition, client: widget.client),
              ),
              fullscreenDialog: true,
            ),
          );
        }

        final definitionsList = ListView(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          children: definitions.map((d) {
            return ContextMenu(
              actions: [
                EntryAction(
                  icon: Icons.notes,
                  name: 'Show',
                  description: 'Show details about this backup definition',
                  handler: () => show(d),
                ),
                EntryAction(
                  icon: Icons.edit,
                  name: 'Update',
                  description: 'Update this backup definition',
                  handler: () => _showDatasetDefinitionForm(currentDevice, d),
                ),
                EntryAction(
                  icon: Icons.delete_forever,
                  name: 'Remove',
                  description: 'Permanently remove this backup definition',
                  handler: () {
                    confirmationDialog(
                      context,
                      title: 'Remove backup definition?',
                      content: Text(
                        'Removing backup definition [${d.info}] will make all associated backups inaccessible!',
                      ),
                      onConfirm: () {
                        final messenger = ScaffoldMessenger.of(context);
                        widget.client.deleteDatasetDefinition(definition: d.id).then((_) {
                          messenger.showSnackBar(const SnackBar(content: Text('Backup definition removed...')));
                        }).onError((e, stackTrace) {
                          messenger.showSnackBar(SnackBar(content: Text('Failed to remove backup definition: [$e]')));
                        });
                      },
                    );
                  },
                  color: theme.colorScheme.error,
                ),
              ],
              child: DatasetDefinitionSummary.build(
                context,
                definition: d,
                client: widget.client,
                onTap: () => show(d),
              ),
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

        final addDefinitionButton = Tooltip(
          message: 'Add definition',
          child: FloatingActionButton.small(
            heroTag: null,
            onPressed: () => _showDatasetDefinitionForm(currentDevice, null),
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
              alignment: Alignment.bottomRight,
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: addDefinitionButton,
              ),
            ),
          ],
        );
      },
    );
  }

  void _showDatasetDefinitionForm(Device currentDevice, DatasetDefinition? existingDefinition) {
    final infoField = formField(
      title: 'Info',
      errorMessage: 'Info cannot be empty',
      controller: TextEditingController(text: existingDefinition?.info ?? ''),
    );

    final redundantCopiesField = formField(
      title: 'Redundant Copies',
      errorMessage: 'Redundant copies must be provided',
      controller: TextEditingController(text: existingDefinition?.redundantCopies.toString()),
      type: TextInputType.number,
    );

    Retention? existingVersions = existingDefinition?.existingVersions;
    final existingVersionsField = retentionField(
      title: 'Existing Versions',
      onChange: (updated) => existingVersions = updated,
      initialRetention: existingDefinition?.existingVersions,
    );

    Retention? removedVersions = existingDefinition?.removedVersions;
    final removedVersionsField = retentionField(
      title: 'Removed Versions',
      onChange: (updated) => removedVersions = updated,
      initialRetention: existingDefinition?.removedVersions,
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return SimpleDialog(
          title: existingDefinition == null
              ? const Text('Create New Dataset Definition')
              : const Text('Update Dataset Definition'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [infoField, redundantCopiesField, existingVersionsField, removedVersionsField],
              submitAction: existingDefinition == null ? 'Create' : 'Update',
              onFormSubmitted: () {
                final messenger = ScaffoldMessenger.of(context);

                if (existingDefinition == null) {
                  final request = CreateDatasetDefinition(
                    info: infoField.controller!.text.trim(),
                    device: currentDevice.id,
                    redundantCopies: int.parse(redundantCopiesField.controller!.text.trim()),
                    existingVersions: existingVersions!,
                    removedVersions: removedVersions!,
                  );

                  widget.client.defineBackup(request: request).then((_) {
                    messenger.showSnackBar(const SnackBar(content: Text('Dataset definition created...')));
                    setState(() {});
                  }).onError((e, stackTrace) {
                    messenger.showSnackBar(SnackBar(content: Text('Failed to create dataset definition: [$e]')));
                  }).whenComplete(() {
                    if (context.mounted) Navigator.pop(context);
                  });
                } else {
                  final request = UpdateDatasetDefinition(
                    info: infoField.controller!.text.trim(),
                    redundantCopies: int.parse(redundantCopiesField.controller!.text.trim()),
                    existingVersions: existingVersions!,
                    removedVersions: removedVersions!,
                  );

                  widget.client.updateBackup(definition: existingDefinition.id, request: request).then((_) {
                    messenger.showSnackBar(const SnackBar(content: Text('Dataset definition updated...')));
                    setState(() {});
                  }).onError((e, stackTrace) {
                    messenger.showSnackBar(SnackBar(content: Text('Failed to update dataset definition: [$e]')));
                  }).whenComplete(() {
                    if (context.mounted) Navigator.pop(context);
                  });
                }
              },
            )
          ],
        );
      },
    );
  }
}
