import 'package:freezed_annotation/freezed_annotation.dart';

part 'update_user_state.freezed.dart';
part 'update_user_state.g.dart';

@freezed
abstract class UpdateUserState with _$UpdateUserState {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateUserState({
    required bool active,
  }) = _UpdateUserState;

  factory UpdateUserState.fromJson(Map<String, Object?> json) => _$UpdateUserStateFromJson(json);
}
