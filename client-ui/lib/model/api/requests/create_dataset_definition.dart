import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:stasis_client_ui/model/datasets/dataset_definition.dart';

part 'create_dataset_definition.freezed.dart';
part 'create_dataset_definition.g.dart';

@freezed
class CreateDatasetDefinition with _$CreateDatasetDefinition {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreateDatasetDefinition({
    required String info,
    required String device,
    required int redundantCopies,
    required Retention existingVersions,
    required Retention removedVersions,
  }) = _CreateDatasetDefinition;

  factory CreateDatasetDefinition.fromJson(Map<String, Object?> json) => _$CreateDatasetDefinitionFromJson(json);
}
