import 'package:freezed_annotation/freezed_annotation.dart';

part 'init_state.freezed.dart';

part 'init_state.g.dart';

@freezed
abstract class InitState with _$InitState {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory InitState({
    required String startup,
    required String? cause,
    required String? message,
  }) = _InitState;

  factory InitState.fromJson(Map<String, Object?> json) => _$InitStateFromJson(json);

  factory InitState.empty() => const InitState(startup: 'none', cause: null, message: null);
}
