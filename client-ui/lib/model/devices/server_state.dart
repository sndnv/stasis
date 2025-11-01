import 'package:freezed_annotation/freezed_annotation.dart';

part 'server_state.freezed.dart';
part 'server_state.g.dart';

@freezed
abstract class ServerState with _$ServerState {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory ServerState({
    required bool reachable,
    required DateTime timestamp,
  }) = _ServerState;

  factory ServerState.fromJson(Map<String, Object?> json) => _$ServerStateFromJson(json);
}
