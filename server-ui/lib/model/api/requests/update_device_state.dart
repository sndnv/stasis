import 'package:freezed_annotation/freezed_annotation.dart';

part 'update_device_state.freezed.dart';
part 'update_device_state.g.dart';

@freezed
class UpdateDeviceState with _$UpdateDeviceState {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateDeviceState({
    required bool active,
  }) = _UpdateDeviceState;

  factory UpdateDeviceState.fromJson(Map<String, Object?> json) => _$UpdateDeviceStateFromJson(json);
}
