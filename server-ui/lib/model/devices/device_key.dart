import 'package:freezed_annotation/freezed_annotation.dart';

part 'device_key.freezed.dart';
part 'device_key.g.dart';

@freezed
class DeviceKey with _$DeviceKey {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory DeviceKey({
    required String owner,
    required String device,
  }) = _DeviceKey;

  factory DeviceKey.fromJson(Map<String, Object?> json) => _$DeviceKeyFromJson(json);
}
