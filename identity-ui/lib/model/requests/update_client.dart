import 'package:freezed_annotation/freezed_annotation.dart';

part 'update_client.freezed.dart';
part 'update_client.g.dart';

@freezed
class UpdateClient with _$UpdateClient {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateClient({
    required int tokenExpiration,
    required bool active,
  }) = _UpdateClient;

  factory UpdateClient.fromJson(Map<String, Object?> json) => _$UpdateClientFromJson(json);
}
