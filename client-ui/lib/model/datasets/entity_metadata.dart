import 'package:freezed_annotation/freezed_annotation.dart';

part 'entity_metadata.freezed.dart';

part 'entity_metadata.g.dart';

abstract class EntityMetadata {
  const EntityMetadata();

  factory EntityMetadata.fromJson(Map<String, dynamic> json) {
    final type = json['entity_type'] as String;
    switch (type) {
      case 'file':
        return FileEntityMetadata.fromJson(json);
      case 'directory':
        return DirectoryEntityMetadata.fromJson(json);
      default:
        throw ArgumentError('Unexpected entity type encountered: [$type]');
    }
  }

  Map<String, dynamic> toJson();
}

extension ExtendedEntityMetadata on EntityMetadata {
  String get path {
    if (this is FileEntityMetadata) {
      return (this as FileEntityMetadata).path;
    } else {
      return (this as DirectoryEntityMetadata).path;
    }
  }

  String? get link {
    if (this is FileEntityMetadata) {
      return (this as FileEntityMetadata).link;
    } else {
      return (this as DirectoryEntityMetadata).link;
    }
  }

  bool get isHidden {
    if (this is FileEntityMetadata) {
      return (this as FileEntityMetadata).isHidden;
    } else {
      return (this as DirectoryEntityMetadata).isHidden;
    }
  }

  DateTime get created {
    if (this is FileEntityMetadata) {
      return (this as FileEntityMetadata).created;
    } else {
      return (this as DirectoryEntityMetadata).created;
    }
  }

  DateTime get updated {
    if (this is FileEntityMetadata) {
      return (this as FileEntityMetadata).updated;
    } else {
      return (this as DirectoryEntityMetadata).updated;
    }
  }

  String get owner {
    if (this is FileEntityMetadata) {
      return (this as FileEntityMetadata).owner;
    } else {
      return (this as DirectoryEntityMetadata).owner;
    }
  }

  String get group {
    if (this is FileEntityMetadata) {
      return (this as FileEntityMetadata).group;
    } else {
      return (this as DirectoryEntityMetadata).group;
    }
  }

  String get permissions {
    if (this is FileEntityMetadata) {
      return (this as FileEntityMetadata).permissions;
    } else {
      return (this as DirectoryEntityMetadata).permissions;
    }
  }

  int get size {
    if (this is FileEntityMetadata) {
      return (this as FileEntityMetadata).size;
    } else {
      return 0;
    }
  }

  double get checksum {
    if (this is FileEntityMetadata) {
      return (this as FileEntityMetadata).checksum;
    } else {
      return 0;
    }
  }

  Map<String, String> get crates {
    if (this is FileEntityMetadata) {
      return (this as FileEntityMetadata).crates;
    } else {
      return {};
    }
  }

  String get compression {
    if (this is FileEntityMetadata) {
      return (this as FileEntityMetadata).compression;
    } else {
      return 'none';
    }
  }
}

@freezed
abstract class FileEntityMetadata extends EntityMetadata with _$FileEntityMetadata {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory FileEntityMetadata({
    required String path,
    required String? link,
    required bool isHidden,
    required DateTime created,
    required DateTime updated,
    required String owner,
    required String group,
    required String permissions,
    required int size,
    required double checksum,
    required Map<String, String> crates,
    required String compression,
    required String entityType,
  }) = _FileEntityMetadata;

  const FileEntityMetadata._();

  factory FileEntityMetadata.fromJson(Map<String, Object?> json) => _$FileEntityMetadataFromJson(json);
}

@freezed
abstract class DirectoryEntityMetadata extends EntityMetadata with _$DirectoryEntityMetadata {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory DirectoryEntityMetadata({
    required String path,
    required String? link,
    required bool isHidden,
    required DateTime created,
    required DateTime updated,
    required String owner,
    required String group,
    required String permissions,
    required String entityType,
  }) = _DirectoryEntityMetadata;

  const DirectoryEntityMetadata._();

  factory DirectoryEntityMetadata.fromJson(Map<String, Object?> json) => _$DirectoryEntityMetadataFromJson(json);
}
