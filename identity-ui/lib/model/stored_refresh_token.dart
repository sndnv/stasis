import 'package:freezed_annotation/freezed_annotation.dart';

part 'stored_refresh_token.freezed.dart';
part 'stored_refresh_token.g.dart';

@freezed
class StoredRefreshToken with _$StoredRefreshToken {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory StoredRefreshToken({
    required String token,
    required String client,
    required String owner,
    String? scope,
    DateTime? expiration,
    required DateTime created,
  }) = _StoredRefreshToken;

  factory StoredRefreshToken.fromJson(Map<String, Object?> json) => _$StoredRefreshTokenFromJson(json);
}
