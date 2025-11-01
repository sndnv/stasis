import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/nodes/crate_store_descriptor.dart';
import 'package:server_ui/model/nodes/node.dart';

part 'update_node.freezed.dart';

part 'update_node.g.dart';

abstract class UpdateNode {
  const UpdateNode();

  factory UpdateNode.local(CrateStoreDescriptor storeDescriptor) =>
      UpdateLocalNode(nodeType: 'local', storeDescriptor: storeDescriptor);

  factory UpdateNode.remoteHttp(HttpEndpointAddress address, bool storageAllowed) =>
      UpdateRemoteHttpNode(nodeType: 'remote-http', address: address, storageAllowed: storageAllowed);

  factory UpdateNode.remoteGrpc(GrpcEndpointAddress address, bool storageAllowed) =>
      UpdateRemoteGrpcNode(nodeType: 'remote-grpc', address: address, storageAllowed: storageAllowed);

  Map<String, dynamic> toJson();
}

@freezed
abstract class UpdateLocalNode extends UpdateNode with _$UpdateLocalNode {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateLocalNode({required String nodeType, required CrateStoreDescriptor storeDescriptor}) =
      _UpdateLocalNode;

  const UpdateLocalNode._();

  factory UpdateLocalNode.fromJson(Map<String, Object?> json) => _$UpdateLocalNodeFromJson(json);
}

@freezed
abstract class UpdateRemoteHttpNode extends UpdateNode with _$UpdateRemoteHttpNode {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateRemoteHttpNode({
    required String nodeType,
    required HttpEndpointAddress address,
    required bool storageAllowed,
  }) = _UpdateRemoteHttpNode;

  const UpdateRemoteHttpNode._();

  factory UpdateRemoteHttpNode.fromJson(Map<String, Object?> json) => _$UpdateRemoteHttpNodeFromJson(json);
}

@freezed
abstract class UpdateRemoteGrpcNode extends UpdateNode with _$UpdateRemoteGrpcNode {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateRemoteGrpcNode({
    required String nodeType,
    required GrpcEndpointAddress address,
    required bool storageAllowed,
  }) = _UpdateRemoteGrpcNode;

  const UpdateRemoteGrpcNode._();

  factory UpdateRemoteGrpcNode.fromJson(Map<String, Object?> json) => _$UpdateRemoteGrpcNodeFromJson(json);
}
