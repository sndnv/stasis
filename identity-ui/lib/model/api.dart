import 'package:freezed_annotation/freezed_annotation.dart';

part 'api.freezed.dart';
part 'api.g.dart';

@freezed
class Api with _$Api {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Api({
    required String id,
    required DateTime created,
    required DateTime updated,
  }) = _Api;

  factory Api.fromJson(Map<String, Object?> json) => _$ApiFromJson(json);
}
