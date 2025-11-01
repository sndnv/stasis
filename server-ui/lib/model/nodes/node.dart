import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/nodes/crate_store_descriptor.dart';
import 'package:server_ui/model/formats.dart';

part 'node.freezed.dart';

part 'node.g.dart';

abstract class Node {
  const Node();

  factory Node.local({
    required String id,
    required CrateStoreDescriptor storeDescriptor,
    required DateTime created,
    required DateTime updated,
  }) => LocalNode(nodeType: 'local', id: id, storeDescriptor: storeDescriptor, created: created, updated: updated);

  factory Node.remoteHttp({
    required String id,
    required HttpEndpointAddress address,
    required bool storageAllowed,
    required DateTime created,
    required DateTime updated,
  }) => RemoteHttpNode(
    nodeType: 'remote-http',
    id: id,
    address: address,
    storageAllowed: storageAllowed,
    created: created,
    updated: updated,
  );

  factory Node.remoteGrpc({
    required String id,
    required GrpcEndpointAddress address,
    required bool storageAllowed,
    required DateTime created,
    required DateTime updated,
  }) => RemoteGrpcNode(
    nodeType: 'remote-grpc',
    id: id,
    address: address,
    storageAllowed: storageAllowed,
    created: created,
    updated: updated,
  );

  factory Node.fromJson(Map<String, dynamic> json) {
    final type = json['node_type'] as String;
    switch (type) {
      case 'local':
        return LocalNode.fromJson(json);
      case 'remote-http':
        return RemoteHttpNode.fromJson(json);
      case 'remote-grpc':
        return RemoteGrpcNode.fromJson(json);
      default:
        throw ArgumentError('Unexpected node type encountered: [$type]');
    }
  }

  Map<String, dynamic> toJson();
}

@freezed
abstract class LocalNode extends Node with _$LocalNode {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory LocalNode({
    required String nodeType,
    required String id,
    required CrateStoreDescriptor storeDescriptor,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime created,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime updated,
  }) = _LocalNode;

  const LocalNode._();

  factory LocalNode.fromJson(Map<String, Object?> json) => _$LocalNodeFromJson(json);
}

@freezed
abstract class RemoteHttpNode extends Node with _$RemoteHttpNode {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory RemoteHttpNode({
    required String nodeType,
    required String id,
    required HttpEndpointAddress address,
    required bool storageAllowed,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime created,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime updated,
  }) = _RemoteHttpNode;

  const RemoteHttpNode._();

  factory RemoteHttpNode.fromJson(Map<String, Object?> json) => _$RemoteHttpNodeFromJson(json);
}

@freezed
abstract class RemoteGrpcNode extends Node with _$RemoteGrpcNode {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory RemoteGrpcNode({
    required String nodeType,
    required String id,
    required GrpcEndpointAddress address,
    required bool storageAllowed,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime created,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime updated,
  }) = _RemoteGrpcNode;

  const RemoteGrpcNode._();

  factory RemoteGrpcNode.fromJson(Map<String, Object?> json) => _$RemoteGrpcNodeFromJson(json);
}

@freezed
abstract class HttpEndpointAddress with _$HttpEndpointAddress {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const HttpEndpointAddress._();

  const factory HttpEndpointAddress({required String uri}) = _HttpEndpointAddress;

  factory HttpEndpointAddress.fromJson(Map<String, Object?> json) => _$HttpEndpointAddressFromJson(json);
}

@freezed
abstract class GrpcEndpointAddress with _$GrpcEndpointAddress {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory GrpcEndpointAddress({required String host, required int port, required bool tlsEnabled}) =
      _GrpcEndpointAddress;

  const GrpcEndpointAddress._();

  factory GrpcEndpointAddress.fromJson(Map<String, Object?> json) => _$GrpcEndpointAddressFromJson(json);
}

extension ExtendedNode on Node {
  String id() {
    switch (actualType()) {
      case const (LocalNode):
        return (this as LocalNode).id;
      case const (RemoteHttpNode):
        return (this as RemoteHttpNode).id;
      case const (RemoteGrpcNode):
        return (this as RemoteGrpcNode).id;
      default:
        throw ArgumentError('Unexpected node type encountered: [$runtimeType]');
    }
  }

  String nodeType() {
    switch (actualType()) {
      case const (LocalNode):
        final node = this as LocalNode;
        return '${node.nodeType} / ${node.storeDescriptor.backendType()}';
      case const (RemoteHttpNode):
        return (this as RemoteHttpNode).nodeType;
      case const (RemoteGrpcNode):
        return (this as RemoteGrpcNode).nodeType;
      default:
        throw ArgumentError('Unexpected node type encountered: [$runtimeType]');
    }
  }

  String address() {
    switch (actualType()) {
      case const (LocalNode):
        final descriptor = (this as LocalNode).storeDescriptor;
        return descriptor.location();
      case const (RemoteHttpNode):
        return (this as RemoteHttpNode).address.uri;
      case const (RemoteGrpcNode):
        final address = (this as RemoteGrpcNode).address;
        return '${address.host}:${address.port.toString()}';
      default:
        throw ArgumentError('Unexpected node type encountered: [$runtimeType]');
    }
  }

  bool storageAllowed() {
    switch (actualType()) {
      case const (LocalNode):
        return true;
      case const (RemoteHttpNode):
        return (this as RemoteHttpNode).storageAllowed;
      case const (RemoteGrpcNode):
        return (this as RemoteGrpcNode).storageAllowed;
      default:
        throw ArgumentError('Unexpected node type encountered: [$runtimeType]');
    }
  }

  Type actualType() {
    if (this is LocalNode) {
      return LocalNode;
    } else if (this is RemoteHttpNode) {
      return RemoteHttpNode;
    } else if (this is RemoteGrpcNode) {
      return RemoteGrpcNode;
    } else {
      throw ArgumentError('Unexpected node type encountered: [$runtimeType]');
    }
  }

  DateTime created() {
    switch (actualType()) {
      case const (LocalNode):
        return (this as LocalNode).created;
      case const (RemoteHttpNode):
        return (this as RemoteHttpNode).created;
      case const (RemoteGrpcNode):
        return (this as RemoteGrpcNode).created;
      default:
        throw ArgumentError('Unexpected node type encountered: [$runtimeType]');
    }
  }

  DateTime updated() {
    switch (actualType()) {
      case const (LocalNode):
        return (this as LocalNode).updated;
      case const (RemoteHttpNode):
        return (this as RemoteHttpNode).updated;
      case const (RemoteGrpcNode):
        return (this as RemoteGrpcNode).updated;
      default:
        throw ArgumentError('Unexpected node type encountered: [$runtimeType]');
    }
  }
}
