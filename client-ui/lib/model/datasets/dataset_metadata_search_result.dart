import 'package:stasis_client_ui/model/datasets/dataset_metadata.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'dataset_metadata_search_result.freezed.dart';
part 'dataset_metadata_search_result.g.dart';

@freezed
class DatasetMetadataSearchResult with _$DatasetMetadataSearchResult {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory DatasetMetadataSearchResult({
    required Map<String, DatasetDefinitionResult?> definitions,
  }) = _DatasetMetadataSearchResult;

  factory DatasetMetadataSearchResult.fromJson(Map<String, Object?> json) =>
      _$DatasetMetadataSearchResultFromJson(json);
}

@freezed
class DatasetDefinitionResult with _$DatasetDefinitionResult {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory DatasetDefinitionResult({
    required String definitionInfo,
    required String entryId,
    required DateTime entryCreated,
    required Map<String, EntityState> matches,
  }) = _DatasetDefinitionResult;

  factory DatasetDefinitionResult.fromJson(Map<String, Object?> json) => _$DatasetDefinitionResultFromJson(json);
}
