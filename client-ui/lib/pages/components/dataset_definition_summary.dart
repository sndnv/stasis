import 'package:flutter/material.dart';
import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/datasets/dataset_definition.dart';
import 'package:stasis_client_ui/pages/components/extensions.dart';
import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:stasis_client_ui/pages/components/sizing.dart';

class DatasetDefinitionSummary {
  static ListTile build(
    BuildContext context, {
    required DatasetDefinition definition,
    required bool isDefault,
    required ClientApi client,
    void Function()? onTap,
    void Function()? onLongPress,
  }) {
    final theme = Theme.of(context);
    final mediumBold = theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold);
    final mediumItalic = theme.textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic);
    final smallBold = theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.bold);

    final media = MediaQuery.of(context);

    final title = RichText(
      text: TextSpan(
        children: [
          TextSpan(text: definition.info, style: mediumBold),
          TextSpan(text: ' (', style: theme.textTheme.bodyMedium),
          TextSpan(text: definition.id.toMinimizedString(), style: mediumItalic),
          TextSpan(text: ')', style: theme.textTheme.bodyMedium),
        ],
      ),
    );

    final subtitle = Padding(
      padding: const EdgeInsets.all(4.0),
      child: RichText(
        text: TextSpan(
          children: [
            TextSpan(text: 'Retention', style: smallBold),
            TextSpan(text: '\n Existing data: ', style: theme.textTheme.bodySmall),
            TextSpan(text: definition.existingVersions.render(), style: smallBold),
            TextSpan(text: '\n Removed data: ', style: theme.textTheme.bodySmall),
            TextSpan(text: definition.removedVersions.render(), style: smallBold),
            TextSpan(text: '\n Redundant copies: ', style: theme.textTheme.bodySmall),
            TextSpan(text: definition.redundantCopies.toString(), style: smallBold),
            TextSpan(text: '\nCreated: ', style: smallBold),
            TextSpan(text: definition.created.render(), style: theme.textTheme.bodySmall),
            TextSpan(text: '\nUpdated: ', style: smallBold),
            TextSpan(text: definition.updated.render(), style: theme.textTheme.bodySmall),
          ],
        ),
      ),
    );

    final trailing = Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        ElevatedButton(
          onPressed: () {
            final messenger = ScaffoldMessenger.of(context);
            client.startBackup(definition: definition.id).then((_) {
              messenger.showSnackBar(const SnackBar(content: Text('Backup started...')));
            }).onError((e, stackTrace) {
              messenger.showSnackBar(SnackBar(content: Text('Failed to start backup: [$e]')));
            });
          },
          child: media.size.width > Sizing.xs
              ? const Text('START BACKUP')
              : const Tooltip(message: 'Start backup', child: Icon(Icons.upload)),
        ),
      ],
    );

    return ListTile(
      title: isDefault
          ? Row(
              children: [
                title,
                Tooltip(
                  message: 'Default backup definition',
                  child: Icon(
                    Icons.check_box_outlined,
                    size: 16.0,
                    color: theme.colorScheme.primary,
                  ),
                ),
              ],
            )
          : title,
      subtitle: subtitle,
      trailing: trailing,
      onTap: onTap,
      onLongPress: onLongPress,
    );
  }
}
