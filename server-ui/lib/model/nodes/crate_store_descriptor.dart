import 'package:freezed_annotation/freezed_annotation.dart';

part 'crate_store_descriptor.freezed.dart';

part 'crate_store_descriptor.g.dart';

abstract class CrateStoreDescriptor {
  CrateStoreDescriptor();

  factory CrateStoreDescriptor.memory({required int maxSize, required int maxChunkSize, required String name}) =>
      StreamingMemoryBackendDescriptor(
        backendType: 'memory',
        maxSize: maxSize,
        maxChunkSize: maxChunkSize,
        name: name,
      );

  factory CrateStoreDescriptor.container({required String path, required int maxChunkSize, required int maxChunks}) =>
      ContainerBackendDescriptor(
        backendType: 'container',
        path: path,
        maxChunkSize: maxChunkSize,
        maxChunks: maxChunks,
      );

  factory CrateStoreDescriptor.file({required String parentDirectory}) => FileBackendDescriptor(
        backendType: 'file',
        parentDirectory: parentDirectory,
      );

  factory CrateStoreDescriptor.fromJson(Map<String, dynamic> json) {
    final type = json['backend_type'] as String;
    switch (type) {
      case 'memory':
        return StreamingMemoryBackendDescriptor.fromJson(json);
      case 'container':
        return ContainerBackendDescriptor.fromJson(json);
      case 'file':
        return FileBackendDescriptor.fromJson(json);
      default:
        throw ArgumentError('Unexpected backend type encountered: [$type]');
    }
  }

  Map<String, dynamic> toJson();
}

@freezed
class StreamingMemoryBackendDescriptor extends CrateStoreDescriptor with _$StreamingMemoryBackendDescriptor {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory StreamingMemoryBackendDescriptor({
    required String backendType,
    required int maxSize,
    required int maxChunkSize,
    required String name,
  }) = _StreamingMemoryBackendDescriptor;

  factory StreamingMemoryBackendDescriptor.fromJson(Map<String, dynamic> json) =>
      _$StreamingMemoryBackendDescriptorFromJson(json);
}

@freezed
class ContainerBackendDescriptor extends CrateStoreDescriptor with _$ContainerBackendDescriptor {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory ContainerBackendDescriptor({
    required String backendType,
    required String path,
    required int maxChunkSize,
    required int maxChunks,
  }) = _ContainerBackendDescriptor;

  factory ContainerBackendDescriptor.fromJson(Map<String, dynamic> json) => _$ContainerBackendDescriptorFromJson(json);
}

@freezed
class FileBackendDescriptor extends CrateStoreDescriptor with _$FileBackendDescriptor {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory FileBackendDescriptor({
    required String backendType,
    required String parentDirectory,
  }) = _FileBackendDescriptor;

  factory FileBackendDescriptor.fromJson(Map<String, dynamic> json) => _$FileBackendDescriptorFromJson(json);
}

extension ExtendedCrateStoreDescriptor on CrateStoreDescriptor {
  String backendType() {
    switch (actualType()) {
      case StreamingMemoryBackendDescriptor:
        return (this as StreamingMemoryBackendDescriptor).backendType;
      case ContainerBackendDescriptor:
        return (this as ContainerBackendDescriptor).backendType;
      case FileBackendDescriptor:
        return (this as FileBackendDescriptor).backendType;
      default:
        throw ArgumentError('Unexpected descriptor type encountered: [$runtimeType]');
    }
  }

  String location() {
    switch (actualType()) {
      case StreamingMemoryBackendDescriptor:
        return 'memory';
      case ContainerBackendDescriptor:
        return (this as ContainerBackendDescriptor).path;
      case FileBackendDescriptor:
        return (this as FileBackendDescriptor).parentDirectory;
      default:
        throw ArgumentError('Unexpected backend type encountered: [$runtimeType]');
    }
  }

  StreamingMemoryBackendDescriptor? asMemory() {
    switch (actualType()) {
      case StreamingMemoryBackendDescriptor:
        return (this as StreamingMemoryBackendDescriptor);
      case ContainerBackendDescriptor:
        return null;
      case FileBackendDescriptor:
        return null;
      default:
        throw ArgumentError('Unexpected descriptor type encountered: [$runtimeType]');
    }
  }

  ContainerBackendDescriptor? asContainer() {
    switch (actualType()) {
      case StreamingMemoryBackendDescriptor:
        return null;
      case ContainerBackendDescriptor:
        return (this as ContainerBackendDescriptor);
      case FileBackendDescriptor:
        return null;
      default:
        throw ArgumentError('Unexpected descriptor type encountered: [$runtimeType]');
    }
  }

  FileBackendDescriptor? asFile() {
    switch (actualType()) {
      case StreamingMemoryBackendDescriptor:
        return null;
      case ContainerBackendDescriptor:
        return null;
      case FileBackendDescriptor:
        return (this as FileBackendDescriptor);
      default:
        throw ArgumentError('Unexpected descriptor type encountered: [$runtimeType]');
    }
  }

  Type actualType() {
    if (this is StreamingMemoryBackendDescriptor) {
      return StreamingMemoryBackendDescriptor;
    } else if (this is ContainerBackendDescriptor) {
      return ContainerBackendDescriptor;
    } else if (this is FileBackendDescriptor) {
      return FileBackendDescriptor;
    } else {
      throw ArgumentError('Unexpected descriptor type encountered: [$runtimeType]');
    }
  }
}
