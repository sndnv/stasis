import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/devices/device.dart';

part 'create_device_privileged.freezed.dart';
part 'create_device_privileged.g.dart';

@freezed
abstract class CreateDevicePrivileged with _$CreateDevicePrivileged {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreateDevicePrivileged({
    required String name,
    String? node,
    required String owner,
    DeviceLimits? limits,
  }) = _CreateDevicePrivileged;

  factory CreateDevicePrivileged.fromJson(Map<String, Object?> json) => _$CreateDevicePrivilegedFromJson(json);
}
