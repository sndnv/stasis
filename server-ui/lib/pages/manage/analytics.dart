import 'dart:convert';

import 'package:data_table_2/data_table_2.dart';
import 'package:flutter/material.dart';
import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/model/analytics/analytics_entry_summary.dart';
import 'package:server_ui/pages/default/components.dart';
import 'package:server_ui/pages/manage/components/entity_table.dart';
import 'package:server_ui/pages/manage/components/extensions.dart';
import 'package:server_ui/pages/manage/components/rendering.dart';

class Analytics extends StatefulWidget {
  const Analytics({super.key, required this.client});

  final AnalyticsApiClient client;

  @override
  State createState() {
    return _AnalyticsState();
  }
}

class _AnalyticsState extends State<Analytics> {
  @override
  Widget build(BuildContext context) {
    final queryFilter = Uri.base.queryParameters['filter']?.trim() ?? '';

    return buildPage<List<AnalyticsEntrySummary>>(
      of: widget.client.getAnalyticsEntries,
      builder: (context, entries) {
        return EntityTable<AnalyticsEntrySummary>(
          entities: entries,
          actions: [],
          appliedFilter: queryFilter,
          filterBy: (entity, filter) {
            final entry = entity as AnalyticsEntrySummary;
            return (entry.id.contains(filter) ||
                entry.runtime.id.contains(filter) ||
                entry.runtime.app.contains(filter) ||
                entry.runtime.jre.contains(filter) ||
                entry.runtime.os.contains(filter));
          },
          header: const Text('Analytics'),
          defaultSortColumn: 6,
          columns: [
            EntityTableColumn(label: 'ID', sortBy: (e) => (e.id as String).toMinimizedString()),
            EntityTableColumn(label: 'Application', sortBy: (e) => e.runtime.app, size: ColumnSize.L),
            EntityTableColumn(label: 'JRE', sortBy: (e) => e.runtime.jre, size: ColumnSize.L),
            EntityTableColumn(label: 'OS', sortBy: (e) => e.runtime.os, size: ColumnSize.L),
            EntityTableColumn(label: 'Events', sortBy: (e) => e.events, size: ColumnSize.S),
            EntityTableColumn(label: 'Failures', sortBy: (e) => e.failures, size: ColumnSize.S),
            EntityTableColumn(label: 'Received', sortBy: (e) => e.received.toString()),
            EntityTableColumn(label: ''),
          ],
          entityToRow: (entity) {
            final entry = entity as AnalyticsEntrySummary;

            final app = entry.runtime.app.split(';');
            final appName = app.elementAtOrNull(0) ?? 'unknown';
            final appVersion = app.elementAtOrNull(1) ?? 'unknown';
            final appBuildTime = DateTime.fromMillisecondsSinceEpoch(int.tryParse(app.elementAtOrNull(2) ?? '') ?? 0);

            final jre = entry.runtime.jre.split(';');
            final jreVersion = jre.elementAtOrNull(0) ?? 'unknown';
            final jreVendor = jre.elementAtOrNull(1) ?? 'unknown';

            final os = entry.runtime.os.split(';');
            final osName = os.elementAtOrNull(0) ?? 'unknown';
            final osVersion = os.elementAtOrNull(1) ?? 'unknown';
            final osArch = os.elementAtOrNull(2) ?? 'unknown';

            return [
              DataCell(entry.id.asShortId()),
              DataCell(
                Tooltip(
                  message:
                      'Name: $appName\n'
                      'Version: $appVersion\n'
                      'Build Time: ${appBuildTime.render()}',
                  child: Text('$appName@$appVersion', overflow: TextOverflow.ellipsis),
                ),
              ),
              DataCell(
                Tooltip(
                  message:
                      'Version: $jreVersion\n'
                      'Vendor: $jreVendor',
                  child: Text(jreVersion, overflow: TextOverflow.ellipsis),
                ),
              ),
              DataCell(
                Tooltip(
                  message:
                      'Name: $osName\n'
                      'Version: $osVersion\n'
                      'Architecture: $osArch',
                  child: Text('$osName@$osVersion', overflow: TextOverflow.ellipsis),
                ),
              ),
              DataCell(Text(entry.events.toString())),
              DataCell(Text(entry.failures.toString())),
              DataCell(
                Tooltip(
                  message:
                      'Created: ${entry.created.render()}\n'
                      'Updated: ${entry.updated.render()}\n'
                      'Received: ${entry.received.render()}',
                  child: Text(entry.received.render(), overflow: TextOverflow.ellipsis),
                ),
              ),
              DataCell(
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    IconButton(
                      tooltip: 'Show Analytics Entry Details',
                      onPressed: () => _showEntryDetails(entry.id),
                      icon: const Icon(Icons.analytics),
                    ),
                    IconButton(
                      tooltip: 'Remove Analytics Entry',
                      onPressed: () => _removeAnalyticsEntry(entry.id),
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

  void _showEntryDetails(String entry) {
    final ctx = context;
    if (!ctx.mounted) return;

    showDialog(
      context: ctx,
      builder: (context) => buildPage(
        of: () => widget.client.getAnalyticsEntry(entry: entry),
        builder: (context, actualEntry) {
          return SimpleDialog(
            title: Text('Details for Analytics Entry [$entry]'),
            contentPadding: const EdgeInsets.symmetric(vertical: 16.0, horizontal: 48.0),
            children: [SelectionArea(child: Text(JsonEncoder.withIndent('    ').convert(actualEntry)))],
          );
        },
      ),
    );
  }

  void _removeAnalyticsEntry(String entry) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Remove analytics entry [$entry]?'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
            TextButton(
              onPressed: () {
                final messenger = ScaffoldMessenger.of(context);

                widget.client
                    .deleteAnalyticsEntry(entry: entry)
                    .then((_) {
                      messenger.showSnackBar(SnackBar(content: Text('Analytics entry [$entry] removed...')));
                      setState(() {});
                    })
                    .onError((e, stackTrace) {
                      messenger.showSnackBar(
                        SnackBar(content: Text('Failed to remove analytics entry [$entry]: [$e]')),
                      );
                    })
                    .whenComplete(() {
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
}
