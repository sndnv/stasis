import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/devices/device.dart';
import 'package:stasis_client_ui/model/devices/server_state.dart';
import 'package:stasis_client_ui/model/users/user.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/extensions.dart';
import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:stasis_client_ui/utils/triple.dart';
import 'package:flutter/material.dart';

class Status extends StatelessWidget {
  const Status({
    super.key,
    required this.client,
  });

  final ClientApi client;

  @override
  Widget build(BuildContext context) {
    return buildPage<Triple<User, Device, Map<String, ServerState>>>(
      of: () => _getData(),
      builder: (context, data) {
        final theme = Theme.of(context);

        final user = data.a;
        final device = data.b;
        final connections = data.c;

        final userLimits = user.limits != null
            ? ListTile(
                title: const Text('Limits'),
                subtitle: Text(user.limits!.maxStorage.renderFileSize()),
                onTap: () {
                  showDialog(
                    context: context,
                    builder: (_) => SimpleDialog(
                      title: Text('Limits for [${user.id.toMinimizedString()}]'),
                      children: [
                        ListTile(
                          title: const Text('Max Devices'),
                          leading: const Icon(Icons.devices),
                          trailing: Text(user.limits!.maxDevices.renderNumber()),
                        ),
                        ListTile(
                          title: const Text('Max Crates'),
                          leading: const Icon(Icons.data_usage),
                          trailing: Text(user.limits!.maxCrates.renderNumber()),
                        ),
                        ListTile(
                          title: const Text('Max Storage'),
                          leading: const Icon(Icons.sd_storage),
                          trailing: Text(user.limits!.maxStorage.renderFileSize()),
                        ),
                        ListTile(
                          title: const Text('Max Storage per Crate'),
                          leading: const Icon(Icons.sd_storage),
                          trailing: Text(user.limits!.maxStoragePerCrate.renderFileSize()),
                        ),
                        ListTile(
                          title: const Text('Min Retention'),
                          leading: const Icon(Icons.lock_clock),
                          trailing: Text(user.limits!.minRetention.render()),
                        ),
                        ListTile(
                          title: const Text('Max Retention'),
                          leading: const Icon(Icons.lock_clock),
                          trailing: Text(user.limits!.maxRetention.render()),
                        ),
                      ],
                    ),
                  );
                },
              )
            : const ListTile(title: Text('Limits'), subtitle: Text('none'));

        final userCreatedDifference = DateTime.now().difference(user.created).renderApproximate();
        final userUpdatedDifference = DateTime.now().difference(user.updated).renderApproximate();

        final deviceLimits = device.limits != null
            ? ListTile(
                title: const Text('Limits'),
                subtitle: Text(device.limits!.maxStorage.renderFileSize()),
                onTap: () {
                  showDialog(
                    context: context,
                    builder: (_) => SimpleDialog(
                      title: Text('Limits for [${device.id.toMinimizedString()}]'),
                      children: [
                        ListTile(
                          title: const Text('Max Crates'),
                          leading: const Icon(Icons.data_usage),
                          trailing: Text(device.limits!.maxCrates.renderNumber()),
                        ),
                        ListTile(
                          title: const Text('Max Storage'),
                          leading: const Icon(Icons.sd_storage),
                          trailing: Text(device.limits!.maxStorage.renderFileSize()),
                        ),
                        ListTile(
                          title: const Text('Max Storage per Crate'),
                          leading: const Icon(Icons.sd_storage),
                          trailing: Text(device.limits!.maxStoragePerCrate.renderFileSize()),
                        ),
                        ListTile(
                          title: const Text('Min Retention'),
                          leading: const Icon(Icons.lock_clock),
                          trailing: Text(device.limits!.minRetention.render()),
                        ),
                        ListTile(
                          title: const Text('Max Retention'),
                          leading: const Icon(Icons.lock_clock),
                          trailing: Text(device.limits!.maxRetention.render()),
                        ),
                      ],
                    ),
                  );
                },
              )
            : const ListTile(title: Text('Limits'), subtitle: Text('none'));

        final deviceCreatedDifference = DateTime.now().difference(device.created).renderApproximate();
        final deviceUpdatedDifference = DateTime.now().difference(device.updated).renderApproximate();

        final userTile = ExpansionTile(
          leading: const Icon(Icons.person),
          title: const Text('User'),
          children: [
            ListTile(
              title: const Text('ID'),
              subtitle: Text(user.id),
            ),
            userLimits,
            ListTile(
              title: const Text('Permissions'),
              subtitle: Wrap(
                children: (user.permissions.toList()..sort())
                    .map((p) => Padding(
                  padding: const EdgeInsets.all(2.0),
                  child: Chip(label: Text(p)),
                ))
                    .toList(),
              ),
            ),
            ListTile(
              title: const Text('Created'),
              subtitle: Text('${user.created.render()} ($userCreatedDifference ago)'),
            ),
            ListTile(
              title: const Text('Last Updated'),
              subtitle: Text('${user.updated.render()} ($userUpdatedDifference ago)'),
            ),
          ],
        );

        final deviceTile = ExpansionTile(
          leading: const Icon(Icons.devices),
          title: const Text('Device'),
          children: [
            ListTile(
              title: const Text('ID'),
              subtitle: Text(device.id),
            ),
            deviceLimits,
            ListTile(
              title: const Text('Name'),
              subtitle: Text(device.name),
            ),
            ListTile(
              title: const Text('Node'),
              subtitle: Text(device.node),
            ),
            ListTile(
              title: const Text('Created'),
              subtitle: Text('${device.created.render()} ($deviceCreatedDifference ago)'),
            ),
            ListTile(
              title: const Text('Last Updated'),
              subtitle: Text('${device.updated.render()} ($deviceUpdatedDifference ago)'),
            ),
          ],
        );

        final connectionsTile = ExpansionTile(
          leading: const Icon(Icons.hub),
          title: const Text('Servers'),
          children: connections.entries.map((e) {
            final server = e.key;
            final state = e.value;

            final lastSeen = state.timestamp.render();
            final lastSeenDifference = DateTime.now().difference(state.timestamp).renderApproximate();

            if (state.reachable) {
              return ListTile(
                title: Text('$server (reachable)'),
                subtitle: Text('Last updated: $lastSeen ($lastSeenDifference ago)'),
              );
            } else {
              return ListTile(
                title: Text(
                  '$server (unreachable)',
                  style: theme.textTheme.titleMedium?.copyWith(color: theme.colorScheme.error),
                ),
                subtitle: Text('Last updated: $lastSeen ($lastSeenDifference ago)'),
                trailing: Icon(Icons.warning_amber, color: theme.colorScheme.error),
              );
            }
          }).toList(),
        );

        return boxed(
          context,
          child: Card(
            margin: const EdgeInsets.all(16.0),
            child: Column(
              mainAxisSize: MainAxisSize.max,
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [userTile, deviceTile, connectionsTile],
            ),
          ),
        );
      },
    );
  }

  Future<Triple<User, Device, Map<String, ServerState>>> _getData() async {
    final user = await client.getSelf();
    final device = await client.getCurrentDevice();
    final connections = await client.getCurrentDeviceConnections();

    return Triple(user, device, connections);
  }
}
