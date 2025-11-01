import 'package:freezed_annotation/freezed_annotation.dart';

part 'update_user_permissions.freezed.dart';
part 'update_user_permissions.g.dart';

@freezed
abstract class UpdateUserPermissions with _$UpdateUserPermissions {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateUserPermissions({
    required Set<String> permissions,
  }) = _UpdateUserPermissions;

  factory UpdateUserPermissions.fromJson(Map<String, Object?> json) => _$UpdateUserPermissionsFromJson(json);
}
