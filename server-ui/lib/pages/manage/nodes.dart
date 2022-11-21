import 'package:flutter/material.dart';
import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/model/api/requests/create_node.dart';
import 'package:server_ui/model/api/requests/update_node.dart';
import 'package:server_ui/model/nodes/node.dart';
import 'package:server_ui/pages/default/components.dart';
import 'package:server_ui/pages/manage/components/entity_form.dart';
import 'package:server_ui/pages/manage/components/entity_table.dart';
import 'package:server_ui/pages/manage/components/forms/node_field.dart';
import 'package:server_ui/pages/manage/components/extensions.dart';

class Nodes extends StatefulWidget {
  const Nodes({super.key, required this.client});

  final NodesApiClient client;

  @override
  State createState() {
    return _NodesState();
  }
}

class _NodesState extends State<Nodes> {
  @override
  Widget build(BuildContext context) {
    final queryFilter = Uri.base.queryParameters['filter']?.trim() ?? '';

    return buildPage<List<Node>>(
      of: () => widget.client.getNodes(),
      builder: (context, nodes) {
        return EntityTable<Node>(
          entities: nodes,
          actions: [
            IconButton(
              tooltip: 'Create New Node',
              onPressed: () => _createNode(context),
              icon: const Icon(Icons.add),
            ),
          ],
          appliedFilter: queryFilter,
          filterBy: (entity, filter) {
            final node = entity as Node;
            return node.id().contains(filter) || node.nodeType().contains(filter) || node.address().contains(filter);
          },
          defaultSortColumn: 1,
          header: const Text('Nodes'),
          columns: [
            EntityTableColumn(label: 'ID', sortBy: (e) => (e.id as String).toMinimizedString()),
            EntityTableColumn(label: 'Type', sortBy: (e) => (e as Node).nodeType()),
            EntityTableColumn(label: 'Address'),
            EntityTableColumn(label: 'Storage', sortBy: (e) => (e as Node).storageAllowed().toString()),
            EntityTableColumn(label: ''),
          ],
          entityToRow: (entity) {
            final node = entity as Node;

            return [
              DataCell(node.id().asShortId()),
              DataCell(Text(node.nodeType())),
              DataCell(node.renderAddress(context)),
              DataCell(Text(node.storageAllowed() ? 'Allowed' : 'Not Allowed')),
              DataCell(
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    IconButton(
                      tooltip: 'Update Node',
                      onPressed: () => _updateNode(context, node),
                      icon: const Icon(Icons.edit),
                    ),
                    IconButton(
                      tooltip: 'Remove Node',
                      onPressed: () => _removeNode(context, node.id()),
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

  void _createNode(BuildContext context) async {
    CreateNode? request;
    final createNodeField = CreateNodeField(onChange: (updated) => request = updated);

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) {
        return SimpleDialog(
          title: const Text('Create New Node'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [createNodeField],
              submitAction: 'Create',
              onFormSubmitted: () {
                final messenger = ScaffoldMessenger.of(context);

                widget.client.createNode(request: request!).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Node created...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to create node: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
            )
          ],
        );
      },
    );
  }

  void _updateNode(BuildContext context, Node existing) async {
    final id = existing.id();

    UpdateNode request = _updateNodeFromExistingNode(existing);
    final updateNodeField = UpdateNodeField(
      onChange: (updated) => request = updated,
      existingNode: existing,
      initialValue: request,
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) {
        return SimpleDialog(
          title: Text('Update Node [${id.toMinimizedString()}]'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [updateNodeField],
              submitAction: 'Update',
              onFormSubmitted: () {
                final messenger = ScaffoldMessenger.of(context);

                widget.client.updateNode(id: id, request: request).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Node updated...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to update node: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
            )
          ],
        );
      },
    );
  }

  void _removeNode(BuildContext context, String id) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Remove node [${id.toMinimizedString()}]?'),
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

                widget.client.deleteNode(id: id).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Node removed...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to remove node: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
              child: const Text('Remove'),
            ),
          ],
        );
      },
    );
  }

  UpdateNode _updateNodeFromExistingNode(Node existing) {
    final nodeType = existing.nodeType().split(' ').first;
    final storageAllowed = existing.storageAllowed();

    switch (nodeType) {
      case 'local':
        return UpdateNode.local((existing as LocalNode).storeDescriptor);
      case 'remote-http':
        return UpdateNode.remoteHttp((existing as RemoteHttpNode).address, storageAllowed);
      case 'remote-grpc':
        return UpdateNode.remoteGrpc((existing as RemoteGrpcNode).address, storageAllowed);
      default:
        throw ArgumentError('Unexpected node type encountered: [$nodeType]');
    }
  }
}
