import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:stasis_client_ui/model/formats.dart';

part 'dataset_definition.freezed.dart';
part 'dataset_definition.g.dart';

@freezed
abstract class DatasetDefinition with _$DatasetDefinition {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory DatasetDefinition({
    required String id,
    required String info,
    required String device,
    required int redundantCopies,
    required Retention existingVersions,
    required Retention removedVersions,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime created,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime updated,
  }) = _DatasetDefinition;

  factory DatasetDefinition.fromJson(Map<String, Object?> json) => _$DatasetDefinitionFromJson(json);
}

@freezed
abstract class Retention with _$Retention {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Retention({
    required Policy policy,
    @JsonKey(fromJson: durationFromJson, toJson: durationToJson) required Duration duration,
  }) = _Retention;

  factory Retention.fromJson(Map<String, Object?> json) => _$RetentionFromJson(json);
}

@freezed
abstract class Policy with _$Policy {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Policy({
    required String policyType,
    required int? versions,
  }) = _Policy;

  factory Policy.fromJson(Map<String, Object?> json) => _$PolicyFromJson(json);
}
