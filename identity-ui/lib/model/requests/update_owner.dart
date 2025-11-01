import 'package:freezed_annotation/freezed_annotation.dart';

part 'update_owner.freezed.dart';
part 'update_owner.g.dart';

@freezed
abstract class UpdateOwner with _$UpdateOwner {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateOwner({
    required List<String> allowedScopes,
    required bool active,
  }) = _UpdateOwner;

  factory UpdateOwner.fromJson(Map<String, Object?> json) => _$UpdateOwnerFromJson(json);
}
