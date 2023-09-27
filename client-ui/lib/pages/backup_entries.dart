import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/datasets/dataset_definition.dart';
import 'package:stasis_client_ui/model/datasets/dataset_entry.dart';
import 'package:stasis_client_ui/model/datasets/dataset_metadata.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/backup_entry_metadata.dart';
import 'package:stasis_client_ui/pages/components/dataset_definition_summary.dart';
import 'package:stasis_client_ui/pages/components/dataset_entry_summary.dart';
import 'package:stasis_client_ui/pages/components/top_bar.dart';
import 'package:stasis_client_ui/utils/pair.dart';
import 'package:flutter/material.dart';

class BackupEntries extends StatelessWidget {
  const BackupEntries({
    super.key,
    required this.definition,
    required this.client,
  });

  final DatasetDefinition definition;
  final ClientApi client;

  @override
  Widget build(BuildContext context) {
    return buildPage<List<Pair<DatasetEntry, DatasetMetadata>>>(
      of: () => _getData(),
      builder: (context, entries) {
        final theme = Theme.of(context);

        final summary = ListView(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          children: [DatasetDefinitionSummary.build(context, definition: definition, client: client)],
        );

        final entriesList = ListView(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          children: (entries..sort((a, b) => b.a.created.compareTo(a.a.created))).map((e) {
            return DatasetEntrySummary.build(
              context,
              entry: e.a,
              metadata: e.b,
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                    builder: (_) => Scaffold(
                      appBar: TopBar.fromTitle(context, 'Backup entry details'),
                      body: BackupEntryMetadata(client: client, entry: e.a, metadata: e.b),
                    ),
                    fullscreenDialog: true,
                  ),
                );
              },
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

  Future<List<Pair<DatasetEntry, DatasetMetadata>>> _getData() async {
    final entries = await client.getDatasetEntriesForDefinition(definition: definition.id);

    return await Future.wait(
      entries.map((e) => client.getDatasetMetadata(entry: e.id).then((metadata) => Pair(e, metadata))),
    );
  }
}
