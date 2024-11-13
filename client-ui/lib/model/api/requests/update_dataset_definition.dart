import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:stasis_client_ui/model/datasets/dataset_definition.dart';

part 'update_dataset_definition.freezed.dart';
part 'update_dataset_definition.g.dart';

@freezed
class UpdateDatasetDefinition with _$UpdateDatasetDefinition {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateDatasetDefinition({
    required String info,
    required int redundantCopies,
    required Retention existingVersions,
    required Retention removedVersions,
  }) = _UpdateDatasetDefinition;

  factory UpdateDatasetDefinition.fromJson(Map<String, Object?> json) => _$UpdateDatasetDefinitionFromJson(json);
}
