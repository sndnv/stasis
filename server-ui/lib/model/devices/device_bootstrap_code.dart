import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/formats.dart';

part 'device_bootstrap_code.freezed.dart';
part 'device_bootstrap_code.g.dart';

@freezed
class DeviceBootstrapCode with _$DeviceBootstrapCode {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory DeviceBootstrapCode({
    required String value,
    required String owner,
    required String device,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime expiresAt,
  }) = _DeviceBootstrapCode;

  factory DeviceBootstrapCode.fromJson(Map<String, Object?> json) => _$DeviceBootstrapCodeFromJson(json);
}
