import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/datasets/dataset_entry.dart';
import 'package:stasis_client_ui/model/datasets/dataset_metadata.dart';
import 'package:stasis_client_ui/model/datasets/entity_metadata.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/extensions.dart';
import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:stasis_client_ui/pages/components/sizing.dart';
import 'package:flutter/material.dart';

class DatasetMetadataEntitySummary {
  static ExpansionTile build(BuildContext context,
      ClientApi client, {
        required DatasetEntry parentEntry,
        required String entity,
        required EntityState state,
        required EntityMetadata? metadataChanged,
        required EntityMetadata? contentChanged,
      }) {
    final theme = Theme.of(context);
    final smallBold = theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.bold);
    final mediumBold = theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold);

    final media = MediaQuery.of(context);

    final split = entity.toSplitPath();
    final parent = split.a;
    final name = split.b;

    final updated = (contentChanged ?? metadataChanged)?.updated;
    final size = (contentChanged ?? metadataChanged)?.size;
    final changed = contentChanged != null ? 'content' : 'metadata';

    final title = Text(name, style: const TextStyle(fontWeight: FontWeight.bold));

    final subtitle = RichText(
      text: TextSpan(
        children: [
          TextSpan(text: '$parent\n', style: theme.textTheme.bodySmall),
          TextSpan(text: 'Updated: ', style: theme.textTheme.bodySmall),
          TextSpan(text: updated?.renderAsDate(), style: smallBold),
          TextSpan(text: ', Size: ', style: theme.textTheme.bodySmall),
          TextSpan(text: size?.renderFileSize(), style: smallBold),
          TextSpan(text: ', Changed: ', style: theme.textTheme.bodySmall),
          TextSpan(text: changed, style: smallBold),
        ],
      ),
    );

    final details = {
      'Path': (contentChanged ?? metadataChanged)?.path ?? '-',
      'Link': (contentChanged ?? metadataChanged)?.link ?? '-',
      'Hidden': ((contentChanged ?? metadataChanged)?.isHidden ?? false) ? 'Yes' : 'No',
      'Created': (contentChanged ?? metadataChanged)?.created.render() ?? '-',
      'Updated': (contentChanged ?? metadataChanged)?.updated.render() ?? '-',
      'Owner': (contentChanged ?? metadataChanged)?.owner ?? '-',
      'Group': (contentChanged ?? metadataChanged)?.group ?? '-',
      'Permissions': (contentChanged ?? metadataChanged)?.permissions ?? '-',
      'Size': (contentChanged ?? metadataChanged)?.size.renderFileSize() ?? '-',
      'Checksum': (contentChanged ?? metadataChanged)?.checksum.toString() ?? '-',
      'Compression': (contentChanged ?? metadataChanged)?.compression ?? '-',
    }
        .entries
        .map(
          (e) =>
          Row(
            children: [
              Expanded(flex: 1, child: Text(e.key, style: theme.textTheme.bodyMedium, textAlign: TextAlign.end)),
              const Padding(padding: EdgeInsets.symmetric(horizontal: 4.0)),
              Expanded(flex: 5, child: Text(e.value, style: mediumBold)),
            ],
          ),
    )
        .toList();

    final recoveryButton = ElevatedButton(
      onPressed: () {
        confirmationDialog(
          context,
          title: 'Recover file [$name]?',
          content: RichText(
            text: TextSpan(
              children: [
                TextSpan(text: 'File saved on ', style: theme.textTheme.bodySmall),
                TextSpan(text: updated?.renderAsDate(), style: smallBold),
                TextSpan(text: ' will be recovered as ', style: theme.textTheme.bodySmall),
                TextSpan(text: entity, style: smallBold),
                TextSpan(text: '.', style: theme.textTheme.bodySmall),
              ],
            ),
          ),
          onConfirm: () {
            _startRecovery(
              context,
              client,
              definition: parentEntry.definition,
              entry: parentEntry.id,
              path: entity,
            );
          },
        );
      },
      child: media.size.width > Sizing.xs
          ? const Text('RECOVER')
          : const Tooltip(message: 'Recover file', child: Icon(Icons.download)),
    );

    const padding = Padding(padding: EdgeInsets.symmetric(vertical: 8.0));

    return ExpansionTile(
      leading: state.entityIcon(),
      title: title,
      subtitle: subtitle,
      children: <Widget>[padding] + details + [padding] + [recoveryButton] + [padding],
    );
  }

  static void _startRecovery(BuildContext context,
      ClientApi client, {
        required String definition,
        required String entry,
        required String path,
      }) {
    final operation = client.recoverFrom(
      definition: definition,
      entry: entry,
      pathQuery: RegExp.escape(path),
      destination: null,
      discardPaths: null,
    );

    final messenger = ScaffoldMessenger.of(context);

    operation.then((_) {
      messenger.showSnackBar(const SnackBar(content: Text('Recovery started...')));
    }).onError((e, stackTrace) {
      messenger.showSnackBar(SnackBar(content: Text('Failed to start recovery: [$e]')));
    }).whenComplete(() => Navigator.pop(context));
  }
}
