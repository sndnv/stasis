import 'package:freezed_annotation/freezed_annotation.dart';

part 'stored_authorization_code.freezed.dart';
part 'stored_authorization_code.g.dart';

@freezed
class StoredAuthorizationCode with _$StoredAuthorizationCode {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory StoredAuthorizationCode({
    required String code,
    required String client,
    required String owner,
    String? scope,
    required DateTime created,
  }) = _StoredAuthorizationCode;

  factory StoredAuthorizationCode.fromJson(Map<String, Object?> json) => _$StoredAuthorizationCodeFromJson(json);
}
