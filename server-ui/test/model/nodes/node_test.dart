import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/nodes/crate_store_descriptor.dart';
import 'package:server_ui/model/nodes/node.dart';

void main() {
  group('A Node should', () {
    test('support creating objects', () {
      final expectedLocal = LocalNode(
        nodeType: 'local',
        id: 'test-id',
        storeDescriptor: CrateStoreDescriptor.file(parentDirectory: '/tmp'),
      );

      final actualLocal = Node.local(
        id: 'test-id',
        storeDescriptor: CrateStoreDescriptor.file(parentDirectory: '/tmp'),
      );

      const actualRemoteHttp = RemoteHttpNode(
        nodeType: 'remote-http',
        id: 'test-id',
        address: HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
      );

      final expectedRemoteHttp = Node.remoteHttp(
        id: 'test-id',
        address: const HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
      );

      const actualRemoteGrpc = RemoteGrpcNode(
        nodeType: 'remote-grpc',
        id: 'test-id',
        address: GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
      );

      final expectedRemoteGrpc = Node.remoteGrpc(
        id: 'test-id',
        address: const GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
      );

      expect(actualLocal, expectedLocal);
      expect(actualRemoteHttp, expectedRemoteHttp);
      expect(actualRemoteGrpc, expectedRemoteGrpc);
    });

    test('support loading nodes from JSON', () {
      const localJson = '{"node_type":"local"'
          ',"id":"test-id",'
          '"store_descriptor":{"backend_type":"file","parent_directory":"/tmp"}}';
      const remoteHttpJson = '{"node_type":"remote-http"'
          ',"id":"test-id",'
          '"address":{"uri":"localhost"},'
          '"storage_allowed":true}';
      const remoteGrpcJson = '{"node_type":"remote-grpc",'
          '"id":"test-id",'
          '"address":{"host":"localhost","port":1234,"tls_enabled":false},'
          '"storage_allowed":false}';

      final expectedLocal = Node.local(
        id: 'test-id',
        storeDescriptor: CrateStoreDescriptor.file(parentDirectory: '/tmp'),
      );

      final expectedRemoteHttp = Node.remoteHttp(
        id: 'test-id',
        address: const HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
      );

      final expectedRemoteGrpc = Node.remoteGrpc(
        id: 'test-id',
        address: const GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
      );

      final actualLocal = Node.fromJson(jsonDecode(localJson));
      final actualRemoteHttp = Node.fromJson(jsonDecode(remoteHttpJson));
      final actualRemoteGrpc = Node.fromJson(jsonDecode(remoteGrpcJson));

      expect(actualLocal, expectedLocal);
      expect(actualRemoteHttp, expectedRemoteHttp);
      expect(actualRemoteGrpc, expectedRemoteGrpc);
    });

    test('fail to load descriptors with invalid backend types', () {
      expect(() => Node.fromJson(jsonDecode('{"node_type":"other"}')), throwsArgumentError);
    });

    test('support extracting its ID', () {
      final Node local = LocalNode(
        nodeType: 'local',
        id: 'test-id1',
        storeDescriptor: CrateStoreDescriptor.file(parentDirectory: '/tmp'),
      );

      const Node remoteHttp = RemoteHttpNode(
        nodeType: 'remote-http',
        id: 'test-id2',
        address: HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
      );

      const Node remoteGrpc = RemoteGrpcNode(
        nodeType: 'remote-grpc',
        id: 'test-id3',
        address: GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
      );

      expect(local.id(), 'test-id1');
      expect(remoteHttp.id(), 'test-id2');
      expect(remoteGrpc.id(), 'test-id3');
    });

    test('support extracting its node type', () {
      final Node local = LocalNode(
        nodeType: 'local',
        id: 'test-id',
        storeDescriptor: CrateStoreDescriptor.file(parentDirectory: '/tmp'),
      );

      const Node remoteHttp = RemoteHttpNode(
        nodeType: 'remote-http',
        id: 'test-id',
        address: HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
      );

      const Node remoteGrpc = RemoteGrpcNode(
        nodeType: 'remote-grpc',
        id: 'test-id',
        address: GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
      );

      expect(local.nodeType(), 'local / file');
      expect(remoteHttp.nodeType(), 'remote-http');
      expect(remoteGrpc.nodeType(), 'remote-grpc');
    });

    test('support extracting its address', () {
      final Node local = LocalNode(
        nodeType: 'local',
        id: 'test-id',
        storeDescriptor: CrateStoreDescriptor.file(parentDirectory: '/tmp'),
      );

      const Node remoteHttp = RemoteHttpNode(
        nodeType: 'remote-http',
        id: 'test-id',
        address: HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
      );

      const Node remoteGrpc = RemoteGrpcNode(
        nodeType: 'remote-grpc',
        id: 'test-id',
        address: GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
      );

      expect(local.address(), '/tmp');
      expect(remoteHttp.address(), 'localhost');
      expect(remoteGrpc.address(), 'localhost:1234');
    });

    test('support extracting its storage capability', () {
      final Node local = LocalNode(
        nodeType: 'local',
        id: 'test-id',
        storeDescriptor: CrateStoreDescriptor.file(parentDirectory: '/tmp'),
      );

      const Node remoteHttp = RemoteHttpNode(
        nodeType: 'remote-http',
        id: 'test-id',
        address: HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
      );

      const Node remoteGrpc = RemoteGrpcNode(
        nodeType: 'remote-grpc',
        id: 'test-id',
        address: GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
      );

      expect(local.storageAllowed(), true);
      expect(remoteHttp.storageAllowed(), true);
      expect(remoteGrpc.storageAllowed(), false);
    });
  });
}
