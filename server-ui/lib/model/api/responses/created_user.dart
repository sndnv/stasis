import 'package:freezed_annotation/freezed_annotation.dart';

part 'created_user.freezed.dart';
part 'created_user.g.dart';

@freezed
class CreatedUser with _$CreatedUser {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreatedUser({
    required String user,
  }) = _CreatedUser;

  factory CreatedUser.fromJson(Map<String, Object?> json) => _$CreatedUserFromJson(json);
}
