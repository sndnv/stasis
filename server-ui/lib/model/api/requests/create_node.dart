import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/nodes/crate_store_descriptor.dart';
import 'package:server_ui/model/nodes/node.dart';

part 'create_node.freezed.dart';

part 'create_node.g.dart';

abstract class CreateNode {
  const CreateNode();

  factory CreateNode.local(CrateStoreDescriptor storeDescriptor) =>
      CreateLocalNode(nodeType: 'local', storeDescriptor: storeDescriptor);

  factory CreateNode.remoteHttp(HttpEndpointAddress address, bool storageAllowed) =>
      CreateRemoteHttpNode(nodeType: 'remote-http', address: address, storageAllowed: storageAllowed);

  factory CreateNode.remoteGrpc(GrpcEndpointAddress address, bool storageAllowed) =>
      CreateRemoteGrpcNode(nodeType: 'remote-grpc', address: address, storageAllowed: storageAllowed);

  Map<String, dynamic> toJson();
}

@freezed
abstract class CreateLocalNode extends CreateNode with _$CreateLocalNode {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreateLocalNode({required String nodeType, required CrateStoreDescriptor storeDescriptor}) =
      _CreateLocalNode;

  const CreateLocalNode._();

  factory CreateLocalNode.fromJson(Map<String, Object?> json) => _$CreateLocalNodeFromJson(json);
}

@freezed
abstract class CreateRemoteHttpNode extends CreateNode with _$CreateRemoteHttpNode {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreateRemoteHttpNode({
    required String nodeType,
    required HttpEndpointAddress address,
    required bool storageAllowed,
  }) = _CreateRemoteHttpNode;

  const CreateRemoteHttpNode._();

  factory CreateRemoteHttpNode.fromJson(Map<String, Object?> json) => _$CreateRemoteHttpNodeFromJson(json);
}

@freezed
abstract class CreateRemoteGrpcNode extends CreateNode with _$CreateRemoteGrpcNode {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreateRemoteGrpcNode({
    required String nodeType,
    required GrpcEndpointAddress address,
    required bool storageAllowed,
  }) = _CreateRemoteGrpcNode;

  const CreateRemoteGrpcNode._();

  factory CreateRemoteGrpcNode.fromJson(Map<String, Object?> json) => _$CreateRemoteGrpcNodeFromJson(json);
}
