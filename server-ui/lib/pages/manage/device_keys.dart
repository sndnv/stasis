import 'package:flutter/material.dart';
import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/model/devices/device_key.dart';
import 'package:server_ui/pages/default/components.dart';
import 'package:server_ui/pages/manage/components/entity_table.dart';
import 'package:server_ui/pages/manage/components/extensions.dart';
import 'package:server_ui/pages/manage/components/rendering.dart';
import 'package:server_ui/pages/page_destinations.dart';

class DeviceKeys extends StatefulWidget {
  const DeviceKeys({
    super.key,
    required this.client,
    required this.privileged,
  });

  final DevicesApiClient client;
  final bool privileged;

  @override
  State createState() {
    return _DeviceKeysState();
  }
}

class _DeviceKeysState extends State<DeviceKeys> {
  @override
  Widget build(BuildContext context) {
    final queryFilter = Uri.base.queryParameters['filter']?.trim() ?? '';

    return buildPage<List<DeviceKey>>(
      of: () => widget.client.getDeviceKeys(privileged: widget.privileged),
      builder: (context, keys) {
        return EntityTable<DeviceKey>(
          entities: keys,
          actions: const [],
          appliedFilter: queryFilter,
          filterBy: (entity, filter) {
            final key = entity as DeviceKey;
            return key.owner.contains(filter) || key.device.contains(filter);
          },
          header: const Text('Device Keys'),
          defaultSortColumn: 2,
          columns: [
            EntityTableColumn(label: 'Device', sortBy: (e) => (e.device as String).toMinimizedString()),
            EntityTableColumn(label: 'Owner', sortBy: (e) => (e.owner as String).toMinimizedString()),
            EntityTableColumn(label: 'Created', sortBy: (e) => e.created.toString()),
            EntityTableColumn(label: ''),
          ],
          entityToRow: (entity) {
            final key = entity as DeviceKey;

            return [
              DataCell(key.device.asShortId(
                link: Link(
                  buildContext: context,
                  destination: PageRouterDestination.devices,
                  withFilter: key.device,
                ),
              )),
              DataCell(key.owner.asShortId(
                link: widget.privileged
                    ? Link(
                        buildContext: context,
                        destination: PageRouterDestination.users,
                        withFilter: key.owner,
                      )
                    : null,
              )),
              DataCell(Text(key.created.render(), overflow: TextOverflow.ellipsis)),
              DataCell(
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    IconButton(
                      tooltip: 'Remove Device Key',
                      onPressed: () => _removeDeviceKey(key.device),
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

  void _removeDeviceKey(String forDevice) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Remove key for device [${forDevice.toMinimizedString()}]?'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () {
                final messenger = ScaffoldMessenger.of(context);

                widget.client.deleteDeviceKey(privileged: widget.privileged, forDevice: forDevice).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Device key removed...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to remove device key: [$e]')));
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
}
