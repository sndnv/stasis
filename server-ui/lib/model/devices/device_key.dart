import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/formats.dart';

part 'device_key.freezed.dart';
part 'device_key.g.dart';

@freezed
class DeviceKey with _$DeviceKey {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory DeviceKey({
    required String owner,
    required String device,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime created,
  }) = _DeviceKey;

  factory DeviceKey.fromJson(Map<String, Object?> json) => _$DeviceKeyFromJson(json);
}
