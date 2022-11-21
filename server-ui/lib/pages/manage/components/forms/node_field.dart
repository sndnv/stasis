import 'package:flutter/material.dart';
import 'package:server_ui/model/api/requests/create_node.dart';
import 'package:server_ui/model/api/requests/update_node.dart';
import 'package:server_ui/model/nodes/node.dart';
import 'package:server_ui/pages/manage/components/forms/crate_store_field.dart';
import 'package:server_ui/pages/manage/components/forms/node_address_field.dart';
import 'package:server_ui/utils/pair.dart';

class CreateNodeField extends StatefulWidget {
  const CreateNodeField({
    super.key,
    required this.onChange,
  });

  final void Function(CreateNode) onChange;

  @override
  State createState() {
    return _CreateNodeFieldState();
  }
}

class _CreateNodeFieldState extends State<CreateNodeField> {
  final List<Pair<String, String>> _nodeTypes = [
    Pair('local', 'Local'),
    Pair('remote-http', 'Remote / HTTP'),
    Pair('remote-grpc', 'Remote / gRPC'),
  ];

  late String _selectedNode = _nodeTypes[0].a;
  CreateNode? _createNode;
  late bool _storageAllowed = false;

  @override
  Widget build(BuildContext context) {
    final nodeTypeInput = DropdownButtonFormField<String>(
      decoration: const InputDecoration(labelText: 'Node Type'),
      value: _selectedNode,
      items: _nodeTypes.map((e) => DropdownMenuItem(value: e.a, child: Text(e.b))).toList(),
      onChanged: (value) {
        if (value != null) {
          setState(() => _selectedNode = value);
        }
      },
    );

    final storageAllowedField = ListTile(
      title: const Text('Storage Allowed'),
      trailing: Switch(
        value: _storageAllowed,
        activeColor: Theme.of(context).colorScheme.primary,
        onChanged: (value) {
          setState(() => _storageAllowed = value);
          _refreshCollectedData();
          if (_createNode != null) {
            widget.onChange(_createNode!);
          }
        },
      ),
    );

    final List<Padding> baseFields =
        [nodeTypeInput, _createField()].map((e) => Padding(padding: const EdgeInsets.all(8.0), child: e)).toList();

    final List<Padding> extraFields = ((_selectedNode == 'remote-http' || _selectedNode == 'remote-grpc')
        ? [Padding(padding: const EdgeInsets.symmetric(horizontal: 8.0), child: storageAllowedField)]
        : []);

    return Column(children: baseFields + extraFields);
  }

  Widget _createField() {
    switch (_selectedNode) {
      case 'local':
        return CrateStoreDescriptorField(
          onChange: (updated) {
            _createNode = CreateNode.local(updated);
            widget.onChange(_createNode!);
          },
        );
      case 'remote-http':
        return HttpEndpointAddressField(
          onChange: (updated) {
            _createNode = CreateNode.remoteHttp(updated, _storageAllowed);
            widget.onChange(_createNode!);
          },
        );
      case 'remote-grpc':
        return GrpcEndpointAddressField(
          onChange: (updated) {
            _createNode = CreateNode.remoteGrpc(updated, _storageAllowed);
            widget.onChange(_createNode!);
          },
        );
      default:
        throw ArgumentError('Unexpected node type encountered: [$_selectedNode]');
    }
  }

  void _refreshCollectedData() {
    switch (_selectedNode) {
      case 'local':
        /* do nothing */ break;
      case 'remote-http':
        _createNode = (_createNode as CreateRemoteHttpNode?)?.copyWith(storageAllowed: _storageAllowed);
        break;
      case 'remote-grpc':
        _createNode = (_createNode as CreateRemoteGrpcNode?)?.copyWith(storageAllowed: _storageAllowed);
        break;
      default:
        throw ArgumentError('Unexpected node type encountered: [$_selectedNode]');
    }
  }
}

class UpdateNodeField extends StatefulWidget {
  const UpdateNodeField({
    super.key,
    required this.onChange,
    required this.existingNode,
    required this.initialValue,
  });

  final void Function(UpdateNode) onChange;
  final Node existingNode;
  final UpdateNode initialValue;

  @override
  State createState() {
    return _UpdateNodeFieldState();
  }
}

class _UpdateNodeFieldState extends State<UpdateNodeField> {
  late final String _nodeType = widget.existingNode.nodeType().split(' ').first;
  late bool _storageAllowed = widget.existingNode.storageAllowed();
  late UpdateNode _updateNode = widget.initialValue;

  @override
  Widget build(BuildContext context) {
    final storageAllowedField = ListTile(
      title: const Text('Storage Allowed'),
      trailing: Switch(
        value: _storageAllowed,
        activeColor: Theme.of(context).colorScheme.primary,
        onChanged: (value) {
          setState(() => _storageAllowed = value);
          _refreshCollectedData();
          widget.onChange(_updateNode);
        },
      ),
    );

    final List<Padding> baseFields =
        [_createField()].map((e) => Padding(padding: const EdgeInsets.all(8.0), child: e)).toList();

    final List<Padding> extraFields = ((_nodeType == 'remote-http' || _nodeType == 'remote-grpc')
        ? [Padding(padding: const EdgeInsets.symmetric(horizontal: 8.0), child: storageAllowedField)]
        : []);

    return Column(children: baseFields + extraFields);
  }

  Widget _createField() {
    switch (_nodeType) {
      case 'local':
        return CrateStoreDescriptorField(
          onChange: (updated) {
            _updateNode = UpdateNode.local(updated);
            widget.onChange(_updateNode);
          },
          initialValue: (widget.existingNode as LocalNode).storeDescriptor,
        );
      case 'remote-http':
        return HttpEndpointAddressField(
          onChange: (updated) {
            _updateNode = UpdateNode.remoteHttp(updated, _storageAllowed);
            widget.onChange(_updateNode);
          },
          initialValue: (widget.existingNode as RemoteHttpNode).address,
        );
      case 'remote-grpc':
        return GrpcEndpointAddressField(
          onChange: (updated) {
            _updateNode = UpdateNode.remoteGrpc(updated, _storageAllowed);
            widget.onChange(_updateNode);
          },
          initialValue: (widget.existingNode as RemoteGrpcNode).address,
        );
      default:
        throw ArgumentError('Unexpected node type encountered: [$_nodeType]');
    }
  }

  void _refreshCollectedData() {
    switch (_nodeType) {
      case 'local':
        /* do nothing */ break;
      case 'remote-http':
        _updateNode = (_updateNode as UpdateRemoteHttpNode).copyWith(storageAllowed: _storageAllowed);
        break;
      case 'remote-grpc':
        _updateNode = (_updateNode as UpdateRemoteGrpcNode).copyWith(storageAllowed: _storageAllowed);
        break;
      default:
        throw ArgumentError('Unexpected node type encountered: [$_nodeType]');
    }
  }
}
