import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/devices/device.dart';

part 'create_device_own.freezed.dart';
part 'create_device_own.g.dart';

@freezed
class CreateDeviceOwn with _$CreateDeviceOwn {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreateDeviceOwn({
    required String name,
    DeviceLimits? limits,
  }) = _CreateDeviceOwn;

  factory CreateDeviceOwn.fromJson(Map<String, Object?> json) => _$CreateDeviceOwnFromJson(json);
}
