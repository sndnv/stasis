import 'package:flutter/material.dart';
import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/model/api/requests/create_dataset_definition.dart';
import 'package:server_ui/model/api/requests/update_dataset_definition.dart';
import 'package:server_ui/model/datasets/dataset_definition.dart';
import 'package:server_ui/pages/default/components.dart';
import 'package:server_ui/pages/manage/components/entity_form.dart';
import 'package:server_ui/pages/manage/components/entity_table.dart';
import 'package:server_ui/pages/manage/components/extensions.dart';
import 'package:server_ui/utils/pair.dart';
import 'package:server_ui/pages/manage/components/rendering.dart';
import 'package:server_ui/pages/manage/entries.dart';
import 'package:server_ui/pages/page_destinations.dart';

class DatasetDefinitions extends StatefulWidget {
  const DatasetDefinitions({
    super.key,
    required this.definitionsClient,
    required this.entriesClient,
    required this.manifestsClient,
    required this.devicesClient,
    required this.privileged,
  });

  final DatasetDefinitionsApiClient definitionsClient;
  final DatasetEntriesApiClient entriesClient;
  final ManifestsApiClient manifestsClient;
  final DevicesApiClient devicesClient;
  final bool privileged;

  @override
  State createState() {
    return _DatasetDefinitionsState();
  }
}

class _DatasetDefinitionsState extends State<DatasetDefinitions> {
  @override
  Widget build(BuildContext context) {
    final queryFilter = Uri.base.queryParameters['filter']?.trim() ?? '';

    return buildPage<List<DatasetDefinition>>(
      of: () => widget.definitionsClient.getDatasetDefinitions(privileged: widget.privileged),
      builder: (context, definitions) {
        return EntityTable<DatasetDefinition>(
          entities: definitions,
          actions: [
            IconButton(
              tooltip: 'Create New Dataset Definition',
              onPressed: () async => _createDatasetDefinition(context),
              icon: const Icon(Icons.add),
            ),
          ],
          appliedFilter: queryFilter,
          filterBy: (entity, filter) {
            final definition = entity as DatasetDefinition;
            return definition.id.contains(filter) ||
                definition.info.contains(filter) ||
                definition.device.contains(filter);
          },
          header: const Text('Dataset Definitions'),
          columns: [
            EntityTableColumn(label: 'ID', sortBy: (e) => (e.id as String).toMinimizedString()),
            EntityTableColumn(label: 'Info', sortBy: (e) => e.info),
            EntityTableColumn(label: 'Device', sortBy: (e) => (e.device as String).toMinimizedString()),
            EntityTableColumn(label: 'Redundant Copies', sortBy: (e) => e.redundantCopies),
            EntityTableColumn(label: 'Retention\nExisting Versions'),
            EntityTableColumn(label: 'Retention\nRemoved Versions'),
            EntityTableColumn(label: ''),
          ],
          entityToRow: (entity) {
            final definition = entity as DatasetDefinition;

            return [
              DataCell(definition.id.asShortId()),
              DataCell(Text(definition.info)),
              DataCell(definition.device.asShortId(
                link: Link(
                  buildContext: context,
                  destination: PageRouterDestination.devices,
                  withFilter: definition.device,
                ),
              )),
              DataCell(Text(definition.redundantCopies.toString())),
              DataCell(Text(definition.existingVersions.render())),
              DataCell(Text(definition.removedVersions.render())),
              DataCell(
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    IconButton(
                      tooltip: 'Show Dataset Entries',
                      onPressed: () => _showDatasetEntries(context, definition.id),
                      icon: const Icon(Icons.list_alt),
                    ),
                    IconButton(
                      tooltip: 'Update Dataset Definition',
                      onPressed: () => _updateDatasetDefinition(context, definition),
                      icon: const Icon(Icons.edit),
                    ),
                    IconButton(
                      tooltip: 'Remove Dataset Definition',
                      onPressed: () => _removeDatasetDefinition(context, definition.id),
                      icon: const Icon(Icons.delete),
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

  void _createDatasetDefinition(BuildContext context) async {
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

    String? device;
    final devicesField = dropdownField(
      title: 'Device',
      items: (await widget.devicesClient.getDevices(privileged: widget.privileged))
          .map((device) => Pair(device.id, device.name))
          .toList(),
      errorMessage: 'A device must be selected',
      onFieldUpdated: (updated) => device = updated,
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
      builder: (_) {
        return SimpleDialog(
          title: const Text('Create New Dataset Definition'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [infoField, redundantCopiesField, devicesField, existingVersionsField, removedVersionsField],
              submitAction: 'Create',
              onFormSubmitted: () {
                final request = CreateDatasetDefinition(
                  info: infoField.controller!.text.trim(),
                  device: device!,
                  redundantCopies: int.parse(redundantCopiesField.controller!.text.trim()),
                  existingVersions: existingVersions!,
                  removedVersions: removedVersions!,
                );

                final messenger = ScaffoldMessenger.of(context);

                widget.definitionsClient
                    .createDatasetDefinition(privileged: widget.privileged, request: request)
                    .then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Dataset definition created...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to create dataset definition: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
            )
          ],
        );
      },
    );
  }

  void _showDatasetEntries(BuildContext context, String definition) {
    showDialog(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        title: Text('Dataset Entries for Definition [${definition.toMinimizedString()}]'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close'),
          ),
        ],
        content: SizedBox(
          width: MediaQuery.of(context).size.width * 0.80,
          height: MediaQuery.of(context).size.height * 0.80,
          child: DatasetEntries(
            definition: definition,
            entriesClient: widget.entriesClient,
            manifestsClient: widget.manifestsClient,
            privileged: widget.privileged,
          ),
        ),
        contentPadding: const EdgeInsets.all(8.0),
      ),
    );
  }

  void _updateDatasetDefinition(BuildContext context, DatasetDefinition existing) {
    final infoField = formField(
      title: 'Info',
      errorMessage: 'Info cannot be empty',
      controller: TextEditingController(text: existing.info),
    );

    final redundantCopiesField = formField(
      title: 'Redundant Copies',
      errorMessage: 'Redundant copies must be provided',
      controller: TextEditingController(text: existing.redundantCopies.toString()),
      type: TextInputType.number,
    );

    Retention existingVersions = existing.existingVersions;
    final existingVersionsField = retentionField(
      title: 'Existing Versions',
      onChange: (updated) => existingVersions = updated,
      initialRetention: existing.existingVersions,
    );

    Retention removedVersions = existing.removedVersions;
    final removedVersionsField = retentionField(
      title: 'Removed Versions',
      onChange: (updated) => removedVersions = updated,
      initialRetention: existing.removedVersions,
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) {
        return SimpleDialog(
          title: Text('Update Dataset Definition [${existing.id.toMinimizedString()}]'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [infoField, redundantCopiesField, existingVersionsField, removedVersionsField],
              submitAction: 'Update',
              onFormSubmitted: () {
                final request = UpdateDatasetDefinition(
                  info: infoField.controller!.text.trim(),
                  redundantCopies: int.parse(redundantCopiesField.controller!.text.trim()),
                  existingVersions: existingVersions,
                  removedVersions: removedVersions,
                );

                final messenger = ScaffoldMessenger.of(context);

                widget.definitionsClient
                    .updateDatasetDefinition(privileged: widget.privileged, id: existing.id, request: request)
                    .then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Dataset definition updated...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to update dataset definition: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
            )
          ],
        );
      },
    );
  }

  void _removeDatasetDefinition(BuildContext context, String id) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Remove dataset definition [${id.toMinimizedString()}]?'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () {
                final messenger = ScaffoldMessenger.of(context);

                widget.definitionsClient.deleteDatasetDefinition(privileged: widget.privileged, id: id).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Dataset definition removed...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to remove dataset definition: [$e]')));
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
