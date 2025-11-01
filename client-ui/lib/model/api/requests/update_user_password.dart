import 'package:freezed_annotation/freezed_annotation.dart';

part 'update_user_password.freezed.dart';
part 'update_user_password.g.dart';

@freezed
abstract class UpdateUserPassword with _$UpdateUserPassword {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateUserPassword({
    required String currentPassword,
    required String newPassword,
  }) = _UpdateUserPassword;

  factory UpdateUserPassword.fromJson(Map<String, Object?> json) => _$UpdateUserPasswordFromJson(json);
}
