import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/datasets/dataset_entry.dart';
import 'package:stasis_client_ui/model/datasets/dataset_metadata.dart';
import 'package:stasis_client_ui/model/datasets/entity_metadata.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/dataset_entry_summary.dart';
import 'package:stasis_client_ui/pages/components/dataset_metadata_entity_summary.dart';
import 'package:stasis_client_ui/pages/components/extensions.dart';
import 'package:stasis_client_ui/pages/components/file_tree.dart';
import 'package:stasis_client_ui/pages/components/top_bar.dart';
import 'package:flutter/material.dart';

class BackupEntryMetadata extends StatefulWidget {
  const BackupEntryMetadata({
    super.key,
    required this.client,
    required this.entry,
    required this.metadata,
  });

  final ClientApi client;

  final DatasetEntry entry;
  final DatasetMetadata metadata;

  @override
  State createState() {
    return _BackupEntryMetadataState();
  }
}

class _BackupEntryMetadataState extends State<BackupEntryMetadata> {
  bool _showChangedFilesOnly = true;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final originalEntities = widget.metadata.filesystem.entities.keys.toList()..sort();

    final entities = _showChangedFilesOnly
        ? originalEntities.where((e) {
            final state = widget.metadata.filesystem.entities[e];
            final metadata = (widget.metadata.contentChanged[e] ?? widget.metadata.metadataChanged[e]);

            final hasChanged = state?.entityState == 'new' || state?.entityState == 'updated';
            final isFile = metadata is FileEntityMetadata;

            return hasChanged && isFile;
          })
        : originalEntities;

    final summary = ListView(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      children: [DatasetEntrySummary.build(context, entry: widget.entry, metadata: widget.metadata)],
    );

    final toggleUnchanged = FloatingActionButton.small(
      heroTag: null,
      onPressed: () {
        setState(() {
          _showChangedFilesOnly = !_showChangedFilesOnly;
        });
      },
      tooltip: 'Show/hide data saved in previous backups',
      child: Icon(_showChangedFilesOnly ? Icons.filter_alt_off : Icons.filter_alt),
    );

    final showAsTree = FloatingActionButton.small(
      heroTag: null,
      onPressed: () {
        final existingStyle = TreeNodeStyle.defaultStyle.copyWith(leaf: 'existing'.entityIcon());
        final updatedStyle = TreeNodeStyle.defaultStyle.copyWith(leaf: 'updated'.entityIcon());
        final newStyle = TreeNodeStyle.defaultStyle.copyWith(leaf: 'new'.entityIcon());

        final tree = FileTree.fromPaths(
          paths: entities.toList(),
          pathStyle: (path) {
            final state = widget.metadata.filesystem.entities[path];

            switch (state?.entityState) {
              case 'existing':
                return existingStyle;
              case 'updated':
                return updatedStyle;
              case 'new':
                return newStyle;
              default:
                return TreeNodeStyle.defaultStyle;
            }
          },
          pathDetails: (path) => path,
          onTap: (path) {
            showDialog(
              context: context,
              builder: (context) {
                return SimpleDialog(
                  children: [
                    DatasetMetadataEntitySummary.build(
                      context,
                      widget.client,
                      parentEntry: widget.entry,
                      entity: path,
                      state: widget.metadata.filesystem.entities[path]!,
                      metadataChanged: widget.metadata.metadataChanged[path],
                      contentChanged: widget.metadata.contentChanged[path],
                    )
                  ],
                );
              },
            );
          },
        );

        Navigator.push(
          context,
          MaterialPageRoute<void>(
            builder: (_) => Scaffold(
              appBar: TopBar.fromTitle(context, 'Backup entry details'),
              body: Card(
                margin: const EdgeInsets.all(16.0),
                child: SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.max,
                    children: [
                      summary,
                      const Padding(padding: EdgeInsets.symmetric(horizontal: 16.0), child: Divider()),
                      Padding(
                        padding: const EdgeInsets.only(left: 16.0, right: 16.0, bottom: 8.0),
                        child: tree,
                      ),
                    ],
                  ),
                ),
              ),
            ),
            fullscreenDialog: true,
          ),
        );
      },
      tooltip: 'Show saved files as a tree',
      child: const Icon(Icons.account_tree),
    );

    final changesList = ListView(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      children: (entities.toList()..sort()).map((e) {
        return DatasetMetadataEntitySummary.build(
          context,
          widget.client,
          parentEntry: widget.entry,
          entity: e,
          state: widget.metadata.filesystem.entities[e]!,
          metadataChanged: widget.metadata.metadataChanged[e],
          contentChanged: widget.metadata.contentChanged[e],
        );
      }).toList(),
    );

    final noEntries = Padding(
      padding: const EdgeInsets.all(16.0),
      child: Center(
        child: Text(
          'No entries',
          style: theme.textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic),
        ),
      ),
    );

    return Stack(
      children: [
        Align(
          alignment: Alignment.topCenter,
          child: boxed(
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
                    child: entities.isNotEmpty ? changesList : noEntries,
                  ),
                ],
              ),
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
              children: entities.isNotEmpty
                  ? [showAsTree, const Padding(padding: EdgeInsets.all(4.0)), toggleUnchanged]
                  : [toggleUnchanged],
            ),
          ),
        ),
      ],
    );
  }
}
