import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/users/user.dart';

part 'create_user.freezed.dart';
part 'create_user.g.dart';

@freezed
abstract class CreateUser with _$CreateUser {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreateUser({
    required String username,
    required String rawPassword,
    required Set<String> permissions,
    UserLimits? limits,
  }) = _CreateUser;

  factory CreateUser.fromJson(Map<String, Object?> json) => _$CreateUserFromJson(json);
}
