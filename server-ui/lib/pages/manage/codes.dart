import 'package:flutter/material.dart';
import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/model/devices/device_bootstrap_code.dart';
import 'package:server_ui/pages/default/components.dart';
import 'package:server_ui/pages/manage/components/entity_table.dart';
import 'package:server_ui/pages/manage/components/extensions.dart';
import 'package:server_ui/pages/manage/components/rendering.dart';
import 'package:server_ui/pages/page_destinations.dart';

class BootstrapCodes extends StatefulWidget {
  const BootstrapCodes({super.key, required this.client, required this.privileged});

  final DeviceBootstrapCodesApiClient client;
  final bool privileged;

  @override
  State createState() {
    return _BootstrapCodesState();
  }
}

class _BootstrapCodesState extends State<BootstrapCodes> {
  @override
  Widget build(BuildContext context) {
    final queryFilter = Uri.base.queryParameters['filter']?.trim() ?? '';

    return buildPage<List<DeviceBootstrapCode>>(
      of: () => widget.client.getBootstrapCodes(privileged: widget.privileged),
      builder: (context, codes) {
        return EntityTable<DeviceBootstrapCode>(
          entities: codes,
          actions: const [],
          appliedFilter: queryFilter,
          filterBy: (entity, filter) {
            final code = entity as DeviceBootstrapCode;
            final info = DeviceBootstrapCode.extractDeviceInfo(code);
            return code.owner.contains(filter) || info.contains(filter) || code.value.contains(filter);
          },
          header: const Text('Device Bootstrap Codes'),
          defaultSortColumn: 3,
          columns: [
            EntityTableColumn(label: 'Device', sortBy: (e) => (DeviceBootstrapCode.extractDeviceInfo(e))),
            EntityTableColumn(label: 'Device Type', sortBy: (e) => (e.target.type as String)),
            EntityTableColumn(label: 'Owner', sortBy: (e) => (e.owner as String).toMinimizedString()),
            EntityTableColumn(label: 'Expiration', sortBy: (e) => e.expiresAt),
            EntityTableColumn(label: ''),
          ],
          entityToRow: (entity) {
            final code = entity as DeviceBootstrapCode;
            final info = DeviceBootstrapCode.extractDeviceInfo(code);

            final Widget device;
            if (code.target.type == 'existing') {
              device = info.asShortId(
                link: Link(buildContext: context, destination: PageRouterDestination.devices, withFilter: info),
              );
            } else {
              device = Text(info);
            }

            return [
              DataCell(device),
              DataCell(Text(code.target.type)),
              DataCell(
                code.owner.asShortId(
                  link: widget.privileged
                      ? Link(buildContext: context, destination: PageRouterDestination.users, withFilter: code.owner)
                      : null,
                ),
              ),
              DataCell(Text(code.expiresAt.render())),
              DataCell(
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    IconButton(
                      tooltip: 'Remove Device Bootstrap Code',
                      onPressed: () => _removeDeviceBootstrapCode(deviceInfo: info, code: code.id),
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

  void _removeDeviceBootstrapCode({required String deviceInfo, required String code}) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Remove bootstrap code for device [$deviceInfo]?'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
            TextButton(
              onPressed: () {
                final messenger = ScaffoldMessenger.of(context);

                widget.client
                    .deleteBootstrapCode(privileged: widget.privileged, code: code)
                    .then((_) {
                      messenger.showSnackBar(const SnackBar(content: Text('Bootstrap code removed...')));
                      setState(() {});
                    })
                    .onError((e, stackTrace) {
                      messenger.showSnackBar(SnackBar(content: Text('Failed to remove bootstrap code: [$e]')));
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
