import 'package:freezed_annotation/freezed_annotation.dart';

part 'create_owner.freezed.dart';
part 'create_owner.g.dart';

@freezed
abstract class CreateOwner with _$CreateOwner {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreateOwner({
    required String username,
    required String rawPassword,
    required List<String> allowedScopes,
    String? subject,
  }) = _CreateOwner;

  factory CreateOwner.fromJson(Map<String, Object?> json) => _$CreateOwnerFromJson(json);
}
