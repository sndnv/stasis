import 'package:freezed_annotation/freezed_annotation.dart';

part 'updated_user_salt.freezed.dart';
part 'updated_user_salt.g.dart';

@freezed
class UpdatedUserSalt with _$UpdatedUserSalt {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdatedUserSalt({
    required String salt,
  }) = _UpdatedUserSalt;

  factory UpdatedUserSalt.fromJson(Map<String, Object?> json) => _$UpdatedUserSaltFromJson(json);
}
