import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:stasis_client_ui/model/formats.dart';

part 'user.freezed.dart';
part 'user.g.dart';

@freezed
class User with _$User {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory User({
    required String id,
    required bool active,
    required Set<String> permissions,
    UserLimits? limits,
  }) = _User;

  factory User.fromJson(Map<String, Object?> json) => _$UserFromJson(json);
}

@freezed
class UserLimits with _$UserLimits {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UserLimits({
    required int maxDevices,
    required int maxCrates,
    required int maxStorage,
    required int maxStoragePerCrate,
    @JsonKey(fromJson: durationFromJson, toJson: durationToJson) required Duration maxRetention,
    @JsonKey(fromJson: durationFromJson, toJson: durationToJson) required Duration minRetention,
  }) = _UserLimits;

  factory UserLimits.fromJson(Map<String, Object?> json) => _$UserLimitsFromJson(json);
}

extension ExtendedUser on User {
  bool viewPrivilegedAllowed() => permissions.contains('view-privileged');
  bool viewServiceAllowed() => permissions.contains('view-service');
  bool managePrivilegedAllowed() => permissions.contains('manage-privileged');
  bool manageServiceAllowed() => permissions.contains('manage-service');
}
