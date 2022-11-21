import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/devices/device.dart';

part 'update_device_limits.freezed.dart';
part 'update_device_limits.g.dart';

@freezed
class UpdateDeviceLimits with _$UpdateDeviceLimits {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateDeviceLimits({
    DeviceLimits? limits,
  }) = _UpdateDeviceLimits;

  factory UpdateDeviceLimits.fromJson(Map<String, Object?> json) => _$UpdateDeviceLimitsFromJson(json);
}
