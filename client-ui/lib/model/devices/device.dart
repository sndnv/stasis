import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:stasis_client_ui/model/formats.dart';

part 'device.freezed.dart';
part 'device.g.dart';

@freezed
class Device with _$Device {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Device({
    required String id,
    required String name,
    required String node,
    required String owner,
    required bool active,
    DeviceLimits? limits,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime created,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime updated,
  }) = _Device;

  factory Device.fromJson(Map<String, Object?> json) => _$DeviceFromJson(json);
}

@freezed
class DeviceLimits with _$DeviceLimits {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory DeviceLimits({
    required int maxCrates,
    required int maxStorage,
    required int maxStoragePerCrate,
    @JsonKey(fromJson: durationFromJson, toJson: durationToJson) required Duration maxRetention,
    @JsonKey(fromJson: durationFromJson, toJson: durationToJson) required Duration minRetention,
  }) = _DeviceLimits;

  factory DeviceLimits.fromJson(Map<String, Object?> json) => _$DeviceLimitsFromJson(json);
}
