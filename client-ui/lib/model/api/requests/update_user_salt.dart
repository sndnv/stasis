import 'package:freezed_annotation/freezed_annotation.dart';

part 'update_user_salt.freezed.dart';
part 'update_user_salt.g.dart';

@freezed
class UpdateUserSalt with _$UpdateUserSalt {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateUserSalt({
    required String currentPassword,
    required String newSalt,
  }) = _UpdateUserSalt;

  factory UpdateUserSalt.fromJson(Map<String, Object?> json) => _$UpdateUserSaltFromJson(json);
}
