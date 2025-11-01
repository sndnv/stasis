import 'package:freezed_annotation/freezed_annotation.dart';

part 'update_owner_credentials.freezed.dart';
part 'update_owner_credentials.g.dart';

@freezed
abstract class UpdateOwnerCredentials with _$UpdateOwnerCredentials {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateOwnerCredentials({
    required String rawPassword,
  }) = _UpdateOwnerCredentials;

  factory UpdateOwnerCredentials.fromJson(Map<String, Object?> json) => _$UpdateOwnerCredentialsFromJson(json);
}
