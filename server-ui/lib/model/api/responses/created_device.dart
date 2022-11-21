import 'package:freezed_annotation/freezed_annotation.dart';

part 'created_device.freezed.dart';
part 'created_device.g.dart';

@freezed
class CreatedDevice with _$CreatedDevice {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreatedDevice({
    required String device,
    required String node,
  }) = _CreatedDevice;

  factory CreatedDevice.fromJson(Map<String, Object?> json) => _$CreatedDeviceFromJson(json);
}
