import 'package:flutter/material.dart';
import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/model/datasets/dataset_entry.dart';
import 'package:server_ui/pages/default/components.dart';
import 'package:server_ui/pages/manage/components/entity_table.dart';
import 'package:server_ui/pages/manage/components/extensions.dart';
import 'package:server_ui/pages/manage/components/rendering.dart';
import 'package:server_ui/pages/page_destinations.dart';

class DatasetEntries extends StatefulWidget {
  const DatasetEntries({
    super.key,
    required this.definition,
    required this.entriesClient,
    required this.manifestsClient,
    required this.privileged,
  });

  final String definition;
  final DatasetEntriesApiClient entriesClient;
  final ManifestsApiClient manifestsClient;
  final bool privileged;

  @override
  State createState() {
    return _DatasetEntriesState();
  }
}

class _DatasetEntriesState extends State<DatasetEntries> {
  @override
  Widget build(BuildContext context) {
    return buildPage<List<DatasetEntry>>(
      of: () => widget.entriesClient.getDatasetEntriesForDefinition(
        privileged: widget.privileged,
        definition: widget.definition,
      ),
      builder: (context, entries) {
        return EntityTable<DatasetEntry>(
          entities: entries,
          filterBy: (entity, filter) {
            final entry = entity as DatasetEntry;
            return entry.id.contains(filter) ||
                entry.data.any((crate) => crate.contains(filter)) ||
                entry.metadata.contains(filter);
          },
          columns: [
            EntityTableColumn(label: 'ID', sortBy: (e) => (e.id as String).toMinimizedString()),
            EntityTableColumn(label: 'Crates', sortBy: (e) => e.data.length),
            EntityTableColumn(label: 'Metadata', sortBy: (e) => (e.metadata as String).toMinimizedString()),
            EntityTableColumn(label: 'Created', sortBy: (e) => e.created),
            EntityTableColumn(label: ''),
          ],
          entityToRow: (entity) {
            final entry = entity as DatasetEntry;

            return [
              DataCell(entry.id.asShortId()),
              DataCell(
                entry.data.isNotEmpty
                    ? entry.data.length.toString().withInfo(() {
                        showDialog(
                          context: context,
                          builder: (_) => SimpleDialog(
                            title: Text('Crates for entry [${entry.id.toMinimizedString()}]'),
                            children: (entry.data.toList()..sort())
                                .map(
                                  (crate) => ListTile(
                                    title: widget.privileged
                                        ? crate.withInfo(() => _showCrateManifest(crate))
                                        : crate.withCopyButton(),
                                    leading: const Icon(Icons.data_usage),
                                  ),
                                )
                                .toList(),
                          ),
                        );
                      })
                    : Text(entry.data.length.toString()),
              ),
              DataCell(entry.metadata.asShortId()),
              DataCell(Text(entry.created.render())),
              DataCell(
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    IconButton(
                      tooltip: 'Remove Dataset Entry',
                      onPressed: () => _removeDatasetEntry(entry.id),
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

  void _removeDatasetEntry(String id) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Remove dataset entry [${id.toMinimizedString()}]?'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
            TextButton(
              onPressed: () {
                final messenger = ScaffoldMessenger.of(context);

                widget.entriesClient.deleteDatasetEntry(privileged: widget.privileged, id: id).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Dataset entry removed...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to remove dataset entry: [$e]')));
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

  void _showCrateManifest(String crate) {
    final ctx = context;

    widget.manifestsClient.getManifest(crate: crate).then((manifest) {
      if (!ctx.mounted) return;

      showDialog(
        context: ctx,
        builder: (BuildContext context) => SimpleDialog(
          title: Text('Manifest for Crate [${crate.toMinimizedString()}]'),
          children: [
            ListTile(
              title: const Text('Crate'),
              leading: const Icon(Icons.data_usage),
              trailing: FittedBox(child: manifest.crate.asShortId()),
            ),
            ListTile(
              title: const Text('Created'),
              leading: const Icon(Icons.more_time),
              trailing: Padding(
                padding: const EdgeInsets.only(right: 16.0),
                child: Text(manifest.created.render()),
              ),
            ),
            ListTile(
              title: const Text('Size'),
              leading: const Icon(Icons.sd_storage),
              trailing: Padding(
                padding: const EdgeInsets.only(right: 16.0),
                child: Text(
                  manifest.size.renderFileSize(),
                ),
              ),
            ),
            ListTile(
              title: const Text('Copies'),
              leading: const Icon(Icons.copy_all),
              trailing: Padding(
                padding: const EdgeInsets.only(right: 16.0),
                child: Text(manifest.copies.toString()),
              ),
            ),
            ListTile(
              title: const Text('Origin'),
              leading: const Icon(Icons.device_hub),
              trailing: FittedBox(
                child: manifest.origin.asShortId(
                  link: Link(
                    buildContext: context,
                    destination: PageRouterDestination.nodes,
                    withFilter: manifest.origin,
                  ),
                ),
              ),
            ),
            ListTile(
              title: const Text('Source'),
              leading: const Icon(Icons.device_hub),
              trailing: FittedBox(
                child: manifest.source.asShortId(
                  link: Link(
                    buildContext: context,
                    destination: PageRouterDestination.nodes,
                    withFilter: manifest.source,
                  ),
                ),
              ),
            ),
            ListTile(
              title: const Text('Destinations'),
              leading: const Icon(Icons.hub),
              trailing: Padding(
                padding: const EdgeInsets.only(right: 16.0),
                child: Text(
                  manifest.destinations.length.toString(),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.only(left: 8.0),
              child: SizedBox(
                width: 448.0,
                child: Wrap(
                  alignment: WrapAlignment.center,
                  children: manifest.destinations
                      .map(
                        (e) => e.asShortId(
                          link: Link(
                            buildContext: context,
                            destination: PageRouterDestination.nodes,
                            withFilter: e,
                          ),
                        ),
                      )
                      .toList(),
                ),
              ),
            ),
          ],
        ),
      );
    }).onError((e, stackTrace) {
      if (!ctx.mounted) return;
      showDialog(
        context: ctx,
        builder: (BuildContext context) => SimpleDialog(
          title: Text('Manifest for Crate [${crate.toMinimizedString()}]'),
          children: [
            Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                SelectionArea(
                  child: Text(
                    e is BadRequest && e.message.contains('could not be found')
                        ? 'Manifest for crate [$crate] was not found'
                        : 'Failed to retrieve manifest for crate [$crate]: [$e]',
                  ),
                ),
              ],
            )
          ],
        ),
      );
    });
  }
}
