import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/model/commands/command.dart';
import 'package:server_ui/pages/default/components.dart';
import 'package:server_ui/pages/manage/components/entity_form.dart';
import 'package:server_ui/pages/manage/components/entity_table.dart';
import 'package:server_ui/pages/manage/components/extensions.dart';
import 'package:server_ui/pages/manage/components/forms/command_parameters_field.dart';
import 'package:server_ui/pages/manage/components/rendering.dart';
import 'package:server_ui/pages/page_destinations.dart';

class Commands extends StatefulWidget {
  const Commands({
    super.key,
    required this.client,
    required this.privileged,
    this.forDevice,
  });

  final DevicesApiClient client;
  final bool privileged;
  final String? forDevice;

  @override
  State createState() {
    return _CommandsState();
  }
}

class _CommandsState extends State<Commands> {
  @override
  Widget build(BuildContext context) {
    final queryFilter = Uri.base.queryParameters['filter']?.trim() ?? '';
    final forDevice = widget.forDevice;

    return buildPage<List<Command>>(
      of: () => forDevice == null
          ? widget.client.getCommands()
          : widget.client.getDeviceCommands(privileged: widget.privileged, forDevice: forDevice),
      builder: (context, commands) {
        return EntityTable<Command>(
          entities: commands,
          actions: [
            IconButton(
              tooltip: 'Create New Command',
              onPressed: () => _createCommand(),
              icon: const Icon(Icons.add),
            ),
            widget.forDevice == null
                ? IconButton(
                    tooltip: 'Truncate Commands',
                    onPressed: () => _truncateCommands(),
                    icon: const Icon(Icons.delete_sweep),
                  )
                : null,
          ].nonNulls.toList(),
          appliedFilter: queryFilter,
          filterBy: (entity, filter) {
            final command = entity as Command;
            return (command.target?.contains(filter) ?? false) || int.tryParse(filter) == command.sequenceId;
          },
          header: widget.forDevice != null ? Text('Commands for Device [${widget.forDevice}]') : const Text('Commands'),
          defaultSortColumn: 4,
          columns: [
            EntityTableColumn(label: 'Sequence ID', sortBy: (e) => e.sequenceId),
            EntityTableColumn(label: 'Source', sortBy: (e) => e.source),
            EntityTableColumn(label: 'Target', sortBy: (e) => e.target ?? ''),
            EntityTableColumn(label: 'Type'),
            EntityTableColumn(label: 'Created', sortBy: (e) => e.created.toString()),
            widget.forDevice == null ? EntityTableColumn(label: '') : null,
          ].nonNulls.toList(),
          entityToRow: (entity) {
            final command = entity as Command;

            return [
              DataCell(Text(command.sequenceId.toString())),
              DataCell(Text(command.source)),
              DataCell(
                command.target?.asShortId(
                      link: Link(
                        buildContext: context,
                        destination: PageRouterDestination.devices,
                        withFilter: command.target!,
                      ),
                    ) ??
                    Text('-'),
              ),
              DataCell(
                Row(
                  mainAxisAlignment: MainAxisAlignment.start,
                  children: [
                    Text(command.parameters.commandType),
                    IconButton(
                      tooltip: 'Show Command Parameters',
                      onPressed: () => _showCommandParameters(command.sequenceId, command.parameters),
                      icon: const Icon(Icons.settings_outlined),
                    ),
                  ],
                ),
              ),
              DataCell(Text(command.created.render(), overflow: TextOverflow.ellipsis)),
              widget.forDevice == null
                  ? DataCell(
                      Row(
                        mainAxisAlignment: MainAxisAlignment.end,
                        children: [
                          IconButton(
                            tooltip: 'Remove Command',
                            onPressed: () => _removeCommand(command.sequenceId),
                            icon: const Icon(Icons.delete),
                          ),
                        ],
                      ),
                    )
                  : null,
            ].nonNulls.toList();
          },
        );
      },
    );
  }

  void _showCommandParameters(int command, CommandParameters parameters) {
    final ctx = context;
    if (!ctx.mounted) return;

    showDialog(
      context: ctx,
      builder: (context) {
        return SimpleDialog(
          title: Text('Parameters for command [$command]'),
          contentPadding: const EdgeInsets.symmetric(vertical: 16.0, horizontal: 48.0),
          children: [
            SelectionArea(
              child: Text(JsonEncoder.withIndent('    ').convert(parameters)),
            )
          ],
        );
      },
    );
  }

  void _createCommand() {
    CommandParameters? commandParameters;
    final commandParametersField = CommandParametersField(
      onChange: (parameters) => commandParameters = parameters,
    );

    final ctx = context;
    if (!ctx.mounted) return;

    showDialog(
      context: ctx,
      barrierDismissible: false,
      builder: (context) {
        return SimpleDialog(
          title: const Text('Create New Broadcast Command'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [commandParametersField],
              submitAction: 'Create',
              onFormSubmitted: () {
                final messenger = ScaffoldMessenger.of(context);
                final request = commandParameters;

                if (request != null) {
                  final forDevice = widget.forDevice;

                  final response = forDevice == null
                      ? widget.client.createCommand(
                          request: request,
                        )
                      : widget.client.createDeviceCommand(
                          privileged: widget.privileged,
                          request: request,
                          forDevice: forDevice,
                        );

                  response.then((_) {
                    messenger.showSnackBar(const SnackBar(content: Text('Command created...')));
                    setState(() {});
                  }).onError((e, stackTrace) {
                    messenger.showSnackBar(SnackBar(content: Text('Failed to create command: [$e]')));
                  }).whenComplete(() {
                    if (context.mounted) Navigator.pop(context);
                  });
                } else {
                  messenger.showSnackBar(
                    SnackBar(content: Text('Failed to create command: [No valid parameters selected]')),
                  );
                }
              },
            )
          ],
        );
      },
    );
  }

  void _removeCommand(int sequenceId) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Remove command [$sequenceId]?'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () {
                final messenger = ScaffoldMessenger.of(context);

                widget.client.deleteCommand(sequenceId: sequenceId).then((_) {
                  messenger.showSnackBar(SnackBar(content: Text('Command [$sequenceId] removed...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to remove command [$sequenceId]: [$e]')));
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

  void _truncateCommands() {
    DateTime olderThan = DateTime.now();
    final olderThanField = dateTimeField(
      title: 'Older Than',
      onChange: (updated) => olderThan = updated,
      useExtendedTitle: false,
    );

    final ctx = context;
    if (!ctx.mounted) return;

    showDialog(
      context: ctx,
      barrierDismissible: false,
      builder: (context) {
        return SimpleDialog(
          title: const Text('Truncate Commands'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [olderThanField],
              submitAction: 'Truncate',
              onFormSubmitted: () {
                final messenger = ScaffoldMessenger.of(context);
                final timestamp = olderThan;

                widget.client.truncateCommands(olderThan: timestamp).then((_) {
                  messenger.showSnackBar(SnackBar(content: Text('Commands truncated...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to truncate commands: [$e]')));
                }).whenComplete(() {
                  if (context.mounted) Navigator.pop(context);
                });
              },
            )
          ],
        );
      },
    );
  }
}
