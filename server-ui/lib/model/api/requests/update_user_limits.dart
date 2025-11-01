import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/users/user.dart';

part 'update_user_limits.freezed.dart';
part 'update_user_limits.g.dart';

@freezed
abstract class UpdateUserLimits with _$UpdateUserLimits {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateUserLimits({
    UserLimits? limits,
  }) = _UpdateUserLimits;

  factory UpdateUserLimits.fromJson(Map<String, Object?> json) => _$UpdateUserLimitsFromJson(json);
}
