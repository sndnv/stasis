import 'package:flutter/material.dart';
import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/datasets/dataset_definition.dart';
import 'package:stasis_client_ui/model/datasets/dataset_entry.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/backup_entry_metadata.dart';
import 'package:stasis_client_ui/pages/components/context/context_menu.dart';
import 'package:stasis_client_ui/pages/components/context/entry_action.dart';
import 'package:stasis_client_ui/pages/components/dataset_definition_summary.dart';
import 'package:stasis_client_ui/pages/components/dataset_entry_summary.dart';
import 'package:stasis_client_ui/pages/components/extensions.dart';
import 'package:stasis_client_ui/pages/components/top_bar.dart';

class BackupEntries extends StatelessWidget {
  const BackupEntries({
    super.key,
    required this.definition,
    required this.isDefault,
    required this.client,
  });

  final DatasetDefinition definition;
  final bool isDefault;
  final ClientApi client;

  @override
  Widget build(BuildContext context) {
    return buildPage<List<DatasetEntry>>(
      of: () => _getData(),
      builder: (context, entries) {
        final theme = Theme.of(context);

        final summary = ListView(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          children: [
            DatasetDefinitionSummary.build(
              context,
              definition: definition,
              isDefault: isDefault,
              client: client,
            ),
          ],
        );

        void show(DatasetEntry entry) {
          Navigator.push(
            context,
            MaterialPageRoute<void>(
              builder: (_) => Scaffold(
                appBar: TopBar.fromTitle(context, 'Backup entry details'),
                body: BackupEntryMetadata(client: client, entry: entry),
              ),
              fullscreenDialog: true,
            ),
          );
        }

        final entriesList = ListView(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          children: (entries..sort((a, b) => b.created.compareTo(a.created))).map((e) {
            return ContextMenu(
              actions: [
                EntryAction(
                  icon: Icons.notes,
                  name: 'Show',
                  description: 'Show details about this backup entry',
                  handler: () => show(e),
                ),
                EntryAction(
                  icon: Icons.delete_forever,
                  name: 'Remove',
                  description: 'Permanently remove this backup entry',
                  color: theme.colorScheme.error,
                  handler: () {
                    confirmationDialog(
                      context,
                      title: 'Remove backup entry?',
                      content: Text(
                        'Removing backup entry [${e.id.toMinimizedString()}] will make all of its data inaccessible!',
                      ),
                      onConfirm: () {
                        final messenger = ScaffoldMessenger.of(context);
                        client
                            .deleteDatasetEntry(entry: e.id)
                            .then((_) {
                              messenger.showSnackBar(const SnackBar(content: Text('Backup entry removed...')));
                            })
                            .onError((e, stackTrace) {
                              messenger.showSnackBar(SnackBar(content: Text('Failed to remove backup entry: [$e]')));
                            })
                            .whenComplete(() {
                              if (context.mounted) Navigator.pop(context);
                            });
                      },
                    );
                  },
                ),
              ],
              child: DatasetEntrySummary.build(
                context,
                entry: e,
                onTap: () => show(e),
              ),
            );
          }).toList(),
        );

        final noEntries = Padding(
          padding: const EdgeInsets.only(top: 4.0, bottom: 12.0),
          child: Center(
            child: Text(
              'No entries',
              style: theme.textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic),
            ),
          ),
        );

        return boxed(
          context,
          child: Card(
            margin: const EdgeInsets.all(16.0),
            child: Column(
              mainAxisSize: MainAxisSize.max,
              children: [
                summary,
                const Padding(padding: EdgeInsets.symmetric(horizontal: 16.0), child: Divider()),
                Padding(
                  padding: const EdgeInsets.only(left: 16.0, right: 16.0, bottom: 8.0),
                  child: entries.isNotEmpty ? entriesList : noEntries,
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Future<List<DatasetEntry>> _getData() async {
    return await client.getDatasetEntriesForDefinition(definition: definition.id);
  }
}
