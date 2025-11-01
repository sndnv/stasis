import 'package:freezed_annotation/freezed_annotation.dart';

part 'created_dataset_definition.freezed.dart';
part 'created_dataset_definition.g.dart';

@freezed
abstract class CreatedDatasetDefinition with _$CreatedDatasetDefinition {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreatedDatasetDefinition({
    required String definition,
  }) = _CreatedDatasetDefinition;

  factory CreatedDatasetDefinition.fromJson(Map<String, Object?> json) => _$CreatedDatasetDefinitionFromJson(json);
}
