import 'package:freezed_annotation/freezed_annotation.dart';

part 'update_client_credentials.freezed.dart';
part 'update_client_credentials.g.dart';

@freezed
class UpdateClientCredentials with _$UpdateClientCredentials {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateClientCredentials({
    required String rawSecret,
  }) = _UpdateClientCredentials;

  factory UpdateClientCredentials.fromJson(Map<String, Object?> json) => _$UpdateClientCredentialsFromJson(json);
}
