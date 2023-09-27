import 'package:stasis_client_ui/model/datasets/dataset_entry.dart';
import 'package:stasis_client_ui/model/datasets/dataset_metadata.dart';
import 'package:stasis_client_ui/pages/components/extensions.dart';
import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:flutter/material.dart';

class DatasetEntrySummary {
  static ListTile build(
    BuildContext context, {
    required DatasetEntry entry,
    required DatasetMetadata metadata,
    void Function()? onTap,
  }) {
    final theme = Theme.of(context);
    final mediumBold = theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold);
    final mediumItalic = theme.textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic);
    final smallBold = theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.bold);

    final title = RichText(
      text: TextSpan(
        children: [
          TextSpan(text: '${entry.created.renderAsDate()}, ${entry.created.renderAsTime()}', style: mediumBold),
          TextSpan(text: ' (', style: theme.textTheme.bodyMedium),
          TextSpan(text: entry.id.toMinimizedString(), style: mediumItalic),
          TextSpan(text: ')', style: theme.textTheme.bodyMedium),
        ],
      ),
    );

    final subtitle = Padding(
      padding: const EdgeInsets.all(4.0),
      child: RichText(
        text: TextSpan(
          children: [
            TextSpan(text: 'Crates: ', style: theme.textTheme.bodySmall),
            TextSpan(text: entry.data.length.toString(), style: smallBold),
            TextSpan(text: ', Changes: ', style: theme.textTheme.bodySmall),
            TextSpan(text: metadata.contentChanged.length.toString(), style: smallBold),
            TextSpan(text: ', Size: ', style: theme.textTheme.bodySmall),
            TextSpan(text: metadata.contentChangedBytes.renderFileSize(), style: smallBold),
          ],
        ),
      ),
    );

    return ListTile(
      title: title,
      subtitle: subtitle,
      visualDensity: VisualDensity.compact,
      onTap: onTap,
    );
  }
}
