import 'package:freezed_annotation/freezed_annotation.dart';

part 'resource_owner.freezed.dart';
part 'resource_owner.g.dart';

@freezed
abstract class ResourceOwner with _$ResourceOwner {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory ResourceOwner({
    required String username,
    required List<String> allowedScopes,
    required bool active,
    String? subject,
    required DateTime created,
    required DateTime updated,
  }) = _ResourceOwner;

  factory ResourceOwner.fromJson(Map<String, Object?> json) => _$ResourceOwnerFromJson(json);
}
