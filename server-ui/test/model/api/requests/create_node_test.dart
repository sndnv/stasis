import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/api/requests/create_node.dart';
import 'package:server_ui/model/nodes/crate_store_descriptor.dart';
import 'package:server_ui/model/nodes/node.dart';

void main() {
  group('A CreateNode should', () {
    test('support creating objects', () {
      final expectedLocal = CreateLocalNode(
        nodeType: 'local',
        storeDescriptor: CrateStoreDescriptor.file(parentDirectory: '/tmp'),
      );

      final actualLocal = CreateNode.local(CrateStoreDescriptor.file(parentDirectory: '/tmp'));

      const expectedRemoteHttp = CreateRemoteHttpNode(
        nodeType: 'remote-http',
        address: HttpEndpointAddress(uri: 'localhost'),
        storageAllowed: true,
      );

      final actualRemoteHttp = CreateNode.remoteHttp(
        const HttpEndpointAddress(uri: 'localhost'),
        true,
      );

      const expectedRemoteGrpc = CreateRemoteGrpcNode(
        nodeType: 'remote-grpc',
        address: GrpcEndpointAddress(host: 'localhost', port: 1234, tlsEnabled: false),
        storageAllowed: false,
      );

      final actualRemoteGrpc = CreateNode.remoteGrpc(
        const GrpcEndpointAddress(
          host: 'localhost',
          port: 1234,
          tlsEnabled: false,
        ),
        false,
      );

      expect(actualLocal, expectedLocal);
      expect(actualRemoteHttp, expectedRemoteHttp);
      expect(actualRemoteGrpc, expectedRemoteGrpc);
    });
  });
}
