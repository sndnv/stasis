import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/nodes/crate_store_descriptor.dart';
import 'package:server_ui/model/nodes/node.dart';

void main() {
  group('A Node should', () {
    test('support creating objects', () {
      final now = DateTime.now();

      final expectedLocal = LocalNode(
        nodeType: 'local',
        id: 'test-id',
        storeDescriptor: CrateStoreDescriptor.file(parentDirectory: '/tmp'),
        created: now,
        updated: now,
      );

      final actualLocal = Node.local(
        id: 'test-id',
        storeDescriptor: CrateStoreDescriptor.file(parentDirectory: '/tmp'),
        created: now,
        updated: now,
      );

      final actualRemoteHttp = RemoteHttpNode(
        nodeType: 'remote-http',
        id: 'test-id',
        address: HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
        created: now,
        updated: now,
      );

      final expectedRemoteHttp = Node.remoteHttp(
        id: 'test-id',
        address: const HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
        created: now,
        updated: now,
      );

      final actualRemoteGrpc = RemoteGrpcNode(
        nodeType: 'remote-grpc',
        id: 'test-id',
        address: GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
        created: now,
        updated: now,
      );

      final expectedRemoteGrpc = Node.remoteGrpc(
        id: 'test-id',
        address: const GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
        created: now,
        updated: now,
      );

      expect(actualLocal, expectedLocal);
      expect(actualRemoteHttp, expectedRemoteHttp);
      expect(actualRemoteGrpc, expectedRemoteGrpc);
    });

    test('support loading nodes from JSON', () {
      final now = DateTime.now();

      final localJson = '{"node_type":"local"'
          ',"id":"test-id",'
          '"store_descriptor":{"backend_type":"file","parent_directory":"/tmp"},'
          '"created":"${now.toIso8601String()}",'
          '"updated":"${now.toIso8601String()}"}';

      final remoteHttpJson = '{"node_type":"remote-http"'
          ',"id":"test-id",'
          '"address":{"uri":"localhost"},'
          '"storage_allowed":true,'
          '"created":"${now.toIso8601String()}",'
          '"updated":"${now.toIso8601String()}"}';

      final remoteGrpcJson = '{"node_type":"remote-grpc",'
          '"id":"test-id",'
          '"address":{"host":"localhost","port":1234,"tls_enabled":false},'
          '"storage_allowed":false,'
          '"created":"${now.toIso8601String()}",'
          '"updated":"${now.toIso8601String()}"}';

      final expectedLocal = Node.local(
        id: 'test-id',
        storeDescriptor: CrateStoreDescriptor.file(parentDirectory: '/tmp'),
        created: now,
        updated: now,
      );

      final expectedRemoteHttp = Node.remoteHttp(
        id: 'test-id',
        address: const HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
        created: now,
        updated: now,
      );

      final expectedRemoteGrpc = Node.remoteGrpc(
        id: 'test-id',
        address: const GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
        created: now,
        updated: now,
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
        created: DateTime.now(),
        updated: DateTime.now(),
      );

      final Node remoteHttp = RemoteHttpNode(
        nodeType: 'remote-http',
        id: 'test-id2',
        address: HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
        created: DateTime.now(),
        updated: DateTime.now(),
      );

      final Node remoteGrpc = RemoteGrpcNode(
        nodeType: 'remote-grpc',
        id: 'test-id3',
        address: GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
        created: DateTime.now(),
        updated: DateTime.now(),
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
        created: DateTime.now(),
        updated: DateTime.now(),
      );

      final Node remoteHttp = RemoteHttpNode(
        nodeType: 'remote-http',
        id: 'test-id',
        address: HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
        created: DateTime.now(),
        updated: DateTime.now(),
      );

      final Node remoteGrpc = RemoteGrpcNode(
        nodeType: 'remote-grpc',
        id: 'test-id',
        address: GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
        created: DateTime.now(),
        updated: DateTime.now(),
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
        created: DateTime.now(),
        updated: DateTime.now(),
      );

      final Node remoteHttp = RemoteHttpNode(
        nodeType: 'remote-http',
        id: 'test-id',
        address: HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
        created: DateTime.now(),
        updated: DateTime.now(),
      );

      final Node remoteGrpc = RemoteGrpcNode(
        nodeType: 'remote-grpc',
        id: 'test-id',
        address: GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
        created: DateTime.now(),
        updated: DateTime.now(),
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
        created: DateTime.now(),
        updated: DateTime.now(),
      );

      final Node remoteHttp = RemoteHttpNode(
        nodeType: 'remote-http',
        id: 'test-id',
        address: HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
        created: DateTime.now(),
        updated: DateTime.now(),
      );

      final Node remoteGrpc = RemoteGrpcNode(
        nodeType: 'remote-grpc',
        id: 'test-id',
        address: GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
        created: DateTime.now(),
        updated: DateTime.now(),
      );

      expect(local.storageAllowed(), true);
      expect(remoteHttp.storageAllowed(), true);
      expect(remoteGrpc.storageAllowed(), false);
    });

    test('support extracting its creation timestamp', () {
      final now = DateTime.now();
      final before = DateTime.now().subtract(Duration(days: 3));

      final Node local = LocalNode(
        nodeType: 'local',
        id: 'test-id',
        storeDescriptor: CrateStoreDescriptor.file(parentDirectory: '/tmp'),
        created: before.subtract(Duration(hours: 1)),
        updated: now,
      );

      final Node remoteHttp = RemoteHttpNode(
        nodeType: 'remote-http',
        id: 'test-id',
        address: HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
        created: before.subtract(Duration(hours: 2)),
        updated: now,
      );

      final Node remoteGrpc = RemoteGrpcNode(
        nodeType: 'remote-grpc',
        id: 'test-id',
        address: GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
        created: before.subtract(Duration(hours: 3)),
        updated: now,
      );

      expect(local.created(), before.subtract(Duration(hours: 1)));
      expect(remoteHttp.created(), before.subtract(Duration(hours: 2)));
      expect(remoteGrpc.created(), before.subtract(Duration(hours: 3)));
    });

    test('support extracting its update timestamp', () {
      final now = DateTime.now();
      final before = DateTime.now().subtract(Duration(days: 3));

      final Node local = LocalNode(
        nodeType: 'local',
        id: 'test-id',
        storeDescriptor: CrateStoreDescriptor.file(parentDirectory: '/tmp'),
        created: before,
        updated: now.subtract(Duration(hours: 1)),
      );

      final Node remoteHttp = RemoteHttpNode(
        nodeType: 'remote-http',
        id: 'test-id',
        address: HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
        created: before,
        updated: now.subtract(Duration(hours: 2)),
      );

      final Node remoteGrpc = RemoteGrpcNode(
        nodeType: 'remote-grpc',
        id: 'test-id',
        address: GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
        created: before,
        updated: now.subtract(Duration(hours: 3)),
      );

      expect(local.updated(), now.subtract(Duration(hours: 1)));
      expect(remoteHttp.updated(), now.subtract(Duration(hours: 2)));
      expect(remoteGrpc.updated(), now.subtract(Duration(hours: 3)));
    });
  });
}
