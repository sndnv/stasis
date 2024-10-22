import 'package:data_table_2/data_table_2.dart';
import 'package:flutter/material.dart';
import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/model/api/requests/create_device_own.dart';
import 'package:server_ui/model/api/requests/create_device_privileged.dart';
import 'package:server_ui/model/api/requests/update_device_limits.dart';
import 'package:server_ui/model/api/requests/update_device_state.dart';
import 'package:server_ui/model/devices/device.dart';
import 'package:server_ui/model/nodes/node.dart';
import 'package:server_ui/pages/default/components.dart';
import 'package:server_ui/pages/manage/components/entity_form.dart';
import 'package:server_ui/pages/manage/components/entity_table.dart';
import 'package:server_ui/pages/manage/components/extensions.dart';
import 'package:server_ui/utils/pair.dart';
import 'package:server_ui/pages/manage/components/rendering.dart';
import 'package:server_ui/pages/page_destinations.dart';

class Devices extends StatefulWidget {
  const Devices({
    super.key,
    required this.devicesClient,
    required this.nodesClient,
    required this.usersClient,
    required this.bootstrapClient,
    required this.privileged,
  });

  final DevicesApiClient devicesClient;
  final NodesApiClient nodesClient;
  final UsersApiClient usersClient;
  final DeviceBootstrapCodesApiClient bootstrapClient;
  final bool privileged;

  @override
  State createState() {
    return _DevicesState();
  }
}

class _DevicesState extends State<Devices> {
  @override
  Widget build(BuildContext context) {
    final queryFilter = Uri.base.queryParameters['filter']?.trim() ?? '';

    return buildPage<List<Device>>(
      of: () => widget.devicesClient.getDevices(privileged: widget.privileged),
      builder: (context, devices) {
        return EntityTable<Device>(
          entities: devices,
          actions: [
            IconButton(
              tooltip: 'Create New Device',
              onPressed: () => widget.privileged ? _createDevicePrivileged() : _createOwnDevice(),
              icon: const Icon(Icons.add),
            ),
          ],
          appliedFilter: queryFilter,
          filterBy: (entity, filter) {
            final device = entity as Device;
            return device.id.contains(filter) ||
                device.name.contains(filter) ||
                device.owner.contains(filter) ||
                device.node.contains(filter);
          },
          header: const Text('Devices'),
          columns: [
            EntityTableColumn(label: 'ID', sortBy: (e) => (e.id as String).toMinimizedString()),
            EntityTableColumn(label: 'Name', sortBy: (e) => e.name),
            EntityTableColumn(label: 'Owner', sortBy: (e) => (e.owner as String).toMinimizedString()),
            EntityTableColumn(label: 'Node', sortBy: (e) => (e.node as String).toMinimizedString()),
            EntityTableColumn(label: 'Active', sortBy: (e) => e.active.toString(), size: ColumnSize.S),
            EntityTableColumn(label: 'Limits', sortBy: (e) => e.limits?.maxStorage.toInt() ?? 0, size: ColumnSize.S),
            EntityTableColumn(label: 'Updated', sortBy: (e) => e.updated.toString()),
            EntityTableColumn(label: ''),
          ],
          entityToRow: (entity) {
            final device = entity as Device;

            return [
              DataCell(
                device.active
                    ? device.id.asShortId()
                    : Row(
                        children: [
                          device.id.asShortId(),
                          const IconButton(
                            tooltip: 'Deactivated device',
                            onPressed: null,
                            icon: Icon(Icons.warning),
                          )
                        ],
                      ),
              ),
              DataCell(Text(device.name)),
              DataCell(device.owner.asShortId(
                link: widget.privileged
                    ? Link(
                        buildContext: context,
                        destination: PageRouterDestination.users,
                        withFilter: device.owner,
                      )
                    : null,
              )),
              DataCell(device.node.asShortId(
                link: widget.privileged
                    ? Link(
                        buildContext: context,
                        destination: PageRouterDestination.nodes,
                        withFilter: device.node,
                      )
                    : null,
              )),
              DataCell(Text(device.active ? 'Yes' : 'No')),
              DataCell(device.limits?.maxStorage.renderFileSize().withInfo(() {
                    final limits = device.limits!;
                    showDialog(
                      context: context,
                      builder: (_) => SimpleDialog(
                        title: Text('Limits for [${device.id.toMinimizedString()}]'),
                        children: [
                          ListTile(
                            title: const Text('Max Crates'),
                            leading: const Icon(Icons.data_usage),
                            trailing: Text(limits.maxCrates.renderNumber()),
                          ),
                          ListTile(
                            title: const Text('Max Storage'),
                            leading: const Icon(Icons.sd_storage),
                            trailing: Text(limits.maxStorage.renderFileSize()),
                          ),
                          ListTile(
                            title: const Text('Max Storage per Crate'),
                            leading: const Icon(Icons.sd_storage),
                            trailing: Text(limits.maxStoragePerCrate.renderFileSize()),
                          ),
                          ListTile(
                            title: const Text('Min Retention'),
                            leading: const Icon(Icons.lock_clock),
                            trailing: Text(limits.minRetention.render()),
                          ),
                          ListTile(
                            title: const Text('Max Retention'),
                            leading: const Icon(Icons.lock_clock),
                            trailing: Text(limits.maxRetention.render()),
                          ),
                        ],
                      ),
                    );
                  }) ??
                  const Text('none')),
              DataCell(
                Tooltip(
                  message: 'Created: ${device.created.render()}\nUpdated: ${device.updated.render()}',
                  child: Text(device.updated.render(), overflow: TextOverflow.ellipsis),
                ),
              ),
              DataCell(
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    IconButton(
                      tooltip: widget.privileged
                          ? 'Cannot generate device bootstrap codes while server management is enabled'
                          : 'Generate bootstrap code',
                      onPressed: widget.privileged ? null : () => _generateDeviceBootstrapCode(device.id),
                      icon: const Icon(Icons.qr_code),
                    ),
                    IconButton(
                      tooltip: 'Update Device State',
                      onPressed: () => _updateDeviceState(device),
                      icon: const Icon(Icons.power_settings_new),
                    ),
                    IconButton(
                      tooltip: 'Update Device Limits',
                      onPressed: () => _updateDeviceLimits(device),
                      icon: const Icon(Icons.data_usage),
                    ),
                    IconButton(
                      tooltip: 'Remove Device',
                      onPressed: () => _removeDevice(device.id),
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

  void _createOwnDevice() async {
    final nameField = formField(
      title: 'Name',
      errorMessage: 'Name cannot be empty',
      controller: TextEditingController(),
    );

    DeviceLimits? limits;
    final limitsField = deviceLimitsField(
      title: 'Device Limits',
      onChange: (updated) => limits = updated,
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return SimpleDialog(
          title: const Text('Create New Device'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [nameField, limitsField],
              submitAction: 'Create',
              onFormSubmitted: () {
                final request = CreateDeviceOwn(
                  name: nameField.controller!.text.trim(),
                  limits: limits,
                );

                final messenger = ScaffoldMessenger.of(context);

                widget.devicesClient.createOwnDevice(request: request).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Device created...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to create device: [$e]')));
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

  void _createDevicePrivileged() {
    widget.nodesClient
        .getNodes()
        .then((nodes) => widget.usersClient.getUsers().then((users) => Pair(nodes, users)))
        .then((nodesAndUsers) {
      final nameField = formField(
        title: 'Name',
        errorMessage: 'Name cannot be empty',
        controller: TextEditingController(),
      );

      String? node;
      final nodesField = dropdownField(
        title: 'Node',
        items: nodesAndUsers.a.map((node) => Pair(node.id(), node.address())).toList(),
        errorMessage: 'A node must be selected',
        onFieldUpdated: (updated) => node = updated,
      );

      String? owner;
      final ownersField = dropdownField(
        title: 'Owner',
        items: nodesAndUsers.b.map((user) => Pair(user.id, user.id.toMinimizedString())).toList(),
        errorMessage: 'An owner must be selected',
        onFieldUpdated: (updated) => owner = updated,
      );

      DeviceLimits? limits;
      final limitsField = deviceLimitsField(
        title: 'Device Limits',
        onChange: (updated) => limits = updated,
      );

      final ctx = context;
      if (!ctx.mounted) return;

      showDialog(
        context: ctx,
        barrierDismissible: false,
        builder: (context) {
          return SimpleDialog(
            title: const Text('Create New Device'),
            contentPadding: const EdgeInsets.all(16),
            children: [
              EntityForm(
                fields: [nameField, nodesField, ownersField, limitsField],
                submitAction: 'Create',
                onFormSubmitted: () {
                  final request = CreateDevicePrivileged(
                    name: nameField.controller!.text.trim(),
                    node: node!,
                    owner: owner!,
                    limits: limits,
                  );

                  final messenger = ScaffoldMessenger.of(context);

                  widget.devicesClient.createDevice(request: request).then((_) {
                    messenger.showSnackBar(const SnackBar(content: Text('Device created...')));
                    setState(() {});
                  }).onError((e, stackTrace) {
                    messenger.showSnackBar(SnackBar(content: Text('Failed to create device: [$e]')));
                  }).whenComplete(() {
                    if (context.mounted) Navigator.pop(context);
                  });
                },
              )
            ],
          );
        },
      );
    });
  }

  void _generateDeviceBootstrapCode(String id) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return AlertDialog(
          title: Text('Generate Bootstrap Code for Device [${id.toMinimizedString()}]?'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: () {
                final messenger = ScaffoldMessenger.of(context);

                widget.bootstrapClient.generateBootstrapCode(forDevice: id).then((code) {
                  if (!context.mounted) return Future.value();
                  Navigator.pop(context);

                  return showDialog(
                    context: context,
                    barrierDismissible: false,
                    builder: (context) {
                      return AlertDialog(
                        title: Text('Bootstrap Information for [${code.device.toMinimizedString()}]'),
                        content: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            ListTile(
                              title: ConstrainedBox(
                                constraints: const BoxConstraints(minWidth: 96.0),
                                child: const Text('Server'),
                              ),
                              leading: const Icon(Icons.apps),
                              trailing: SelectionArea(
                                child: RichText(
                                  text: TextSpan(
                                    text: widget.bootstrapClient.server,
                                    style: Theme.of(context).textTheme.titleLarge,
                                  ),
                                ).withCopyButton(copyText: widget.bootstrapClient.server),
                              ),
                            ),
                            ListTile(
                              title: const Text('Code'),
                              leading: const Icon(Icons.qr_code),
                              trailing: SelectionArea(
                                child: RichText(
                                  text: TextSpan(
                                    text: code.value,
                                    style:
                                        Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
                                  ),
                                ).withCopyButton(copyText: code.value),
                              ),
                            ),
                          ],
                        ),
                        actions: [
                          ElevatedButton(
                            onPressed: () => Navigator.pop(context),
                            child: const Text('Close'),
                          )
                        ],
                      );
                    },
                  );
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to generate bootstrap code for device: [$e]')));
                  if (context.mounted) Navigator.pop(context);
                });
              },
              child: const Text('Generate'),
            ),
          ],
        );
      },
    );
  }

  void _updateDeviceState(Device existing) {
    bool currentActiveState = existing.active;
    final activeField = stateField(
      title: 'State',
      initialState: existing.active,
      onChange: (updated) => currentActiveState = updated,
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return SimpleDialog(
          title: Text('Update State for Device [${existing.id.toMinimizedString()}]'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [activeField],
              submitAction: 'Update',
              onFormSubmitted: () {
                final request = UpdateDeviceState(active: currentActiveState);

                final messenger = ScaffoldMessenger.of(context);

                widget.devicesClient
                    .updateDeviceState(privileged: widget.privileged, id: existing.id, request: request)
                    .then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Device updated...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to update device: [$e]')));
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

  void _updateDeviceLimits(Device existing) async {
    DeviceLimits? limits;
    final limitsField = deviceLimitsField(
      title: 'Device Limits',
      onChange: (updated) => limits = updated,
      initialDeviceLimits: existing.limits,
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return SimpleDialog(
          title: Text('Update Device [${existing.id.toMinimizedString()}]'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [limitsField],
              submitAction: 'Update',
              onFormSubmitted: () {
                final request = UpdateDeviceLimits(limits: limits);

                final messenger = ScaffoldMessenger.of(context);

                widget.devicesClient
                    .updateDeviceLimits(privileged: widget.privileged, id: existing.id, request: request)
                    .then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Device updated...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to update device: [$e]')));
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

  void _removeDevice(String id) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Remove device [${id.toMinimizedString()}]?'),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(context);
              },
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () {
                final messenger = ScaffoldMessenger.of(context);

                widget.devicesClient.deleteDevice(privileged: widget.privileged, id: id).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Device removed...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to remove device: [$e]')));
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
