import 'package:freezed_annotation/freezed_annotation.dart';

part 'create_api.freezed.dart';
part 'create_api.g.dart';

@freezed
class CreateApi with _$CreateApi {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreateApi({
    required String id,
  }) = _CreateApi;

  factory CreateApi.fromJson(Map<String, Object?> json) => _$CreateApiFromJson(json);
}
