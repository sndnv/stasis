import 'package:stasis_client_ui/model/datasets/entity_metadata.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'dataset_metadata.freezed.dart';

part 'dataset_metadata.g.dart';

@freezed
class DatasetMetadata with _$DatasetMetadata {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory DatasetMetadata({
    required Map<String, EntityMetadata> contentChanged,
    required Map<String, EntityMetadata> metadataChanged,
    required FilesystemMetadata filesystem,
  }) = _DatasetMetadata;

  factory DatasetMetadata.fromJson(Map<String, Object?> json) => _$DatasetMetadataFromJson(json);
}

extension ExtendedDatasetMetadata on DatasetMetadata {
  int get contentChangedBytes => contentChanged.values.fold(0, (collected, metadata) {
        if (metadata is FileEntityMetadata) {
          return collected + metadata.size;
        } else {
          return collected;
        }
      });
}

@freezed
class FilesystemMetadata with _$FilesystemMetadata {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory FilesystemMetadata({
    required Map<String, EntityState> entities,
  }) = _FilesystemMetadata;

  factory FilesystemMetadata.fromJson(Map<String, Object?> json) => _$FilesystemMetadataFromJson(json);
}

@freezed
class EntityState with _$EntityState {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory EntityState({
    required String entityState,
    required String? entry,
  }) = _EntityState;

  factory EntityState.fromJson(Map<String, Object?> json) => _$EntityStateFromJson(json);
}
