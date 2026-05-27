import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/api/requests/create_node.dart';
import 'package:server_ui/model/api/requests/update_node.dart';
import 'package:server_ui/model/api/responses/created_node.dart';
import 'package:server_ui/model/nodes/crate_store_descriptor.dart';
import 'package:server_ui/model/nodes/node.dart';

import '../assertions.dart';

void main() {
  group('Node interop', () {
    test('decode and re-encode CreateNode.local', () {
      assertInterop(
        domain: 'nodes',
        resource: 'CreateNode.local',
        matches: const CreateLocalNode(
          nodeType: 'local',
          storeDescriptor: FileBackendDescriptor(
            backendType: 'file',
            parentDirectory: '/var/stasis/crates',
          ),
        ),
        fromJson: CreateLocalNode.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode CreateNode.remote-http', () {
      assertInterop(
        domain: 'nodes',
        resource: 'CreateNode.remote-http',
        matches: const CreateRemoteHttpNode(
          nodeType: 'remote-http',
          address: HttpEndpointAddress(uri: 'https://core.example.test'),
          storageAllowed: true,
        ),
        fromJson: CreateRemoteHttpNode.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode CreateNode.remote-grpc', () {
      assertInterop(
        domain: 'nodes',
        resource: 'CreateNode.remote-grpc',
        matches: const CreateRemoteGrpcNode(
          nodeType: 'remote-grpc',
          address: GrpcEndpointAddress(host: 'core.example.test', port: 9999, tlsEnabled: true),
          storageAllowed: false,
        ),
        fromJson: CreateRemoteGrpcNode.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode UpdateNode.local', () {
      assertInterop(
        domain: 'nodes',
        resource: 'UpdateNode.local',
        matches: const UpdateLocalNode(
          nodeType: 'local',
          storeDescriptor: StreamingMemoryBackendDescriptor(
            backendType: 'memory',
            maxSize: 1073741824,
            maxChunkSize: 1048576,
            name: 'memory-backend',
          ),
        ),
        fromJson: UpdateLocalNode.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode UpdateNode.remote-http', () {
      assertInterop(
        domain: 'nodes',
        resource: 'UpdateNode.remote-http',
        matches: const UpdateRemoteHttpNode(
          nodeType: 'remote-http',
          address: HttpEndpointAddress(uri: 'https://core-updated.example.test'),
          storageAllowed: false,
        ),
        fromJson: UpdateRemoteHttpNode.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode UpdateNode.remote-grpc', () {
      assertInterop(
        domain: 'nodes',
        resource: 'UpdateNode.remote-grpc',
        matches: const UpdateRemoteGrpcNode(
          nodeType: 'remote-grpc',
          address: GrpcEndpointAddress(host: 'core-updated.example.test', port: 8888, tlsEnabled: false),
          storageAllowed: true,
        ),
        fromJson: UpdateRemoteGrpcNode.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode CreatedNode', () {
      assertInterop(
        domain: 'nodes',
        resource: 'CreatedNode',
        matches: const CreatedNode(node: 'fc60b46a-902a-4324-9fa5-d6c791f6e2c9'),
        fromJson: CreatedNode.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
