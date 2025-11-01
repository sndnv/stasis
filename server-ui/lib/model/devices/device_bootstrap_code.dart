import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/api/requests/create_device_own.dart';
import 'package:server_ui/model/formats.dart';

part 'device_bootstrap_code.freezed.dart';

part 'device_bootstrap_code.g.dart';

@freezed
abstract class DeviceBootstrapCode with _$DeviceBootstrapCode {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory DeviceBootstrapCode({
    required String id,
    required String value,
    required String owner,
    required DeviceBootstrapCodeTarget target,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime expiresAt,
  }) = _DeviceBootstrapCode;

  static String extractDeviceInfo(DeviceBootstrapCode code) => switch (code.target.type) {
        'existing' => code.target.device!,
        'new' => code.target.request!.name,
        _ => throw ArgumentError('Unexpected device bootstrap code target encountered: [${code.target.type}]'),
      };

  factory DeviceBootstrapCode.withDevice({
    required String id,
    required String value,
    required String owner,
    required String device,
    required DateTime expiresAt,
  }) =>
      DeviceBootstrapCode(
        id: id,
        value: value,
        owner: owner,
        target: DeviceBootstrapCodeTarget(
          type: 'existing',
          device: device,
        ),
        expiresAt: expiresAt,
      );

  factory DeviceBootstrapCode.withRequest({
    required String id,
    required String value,
    required String owner,
    required CreateDeviceOwn request,
    required DateTime expiresAt,
  }) =>
      DeviceBootstrapCode(
        id: id,
        value: value,
        owner: owner,
        target: DeviceBootstrapCodeTarget(
          type: 'new',
          request: request,
        ),
        expiresAt: expiresAt,
      );

  factory DeviceBootstrapCode.fromJson(Map<String, Object?> json) => _$DeviceBootstrapCodeFromJson(json);
}

@freezed
abstract class DeviceBootstrapCodeTarget with _$DeviceBootstrapCodeTarget {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory DeviceBootstrapCodeTarget({
    required String type,
    CreateDeviceOwn? request,
    String? device,
  }) = _DeviceBootstrapCodeTarget;

  factory DeviceBootstrapCodeTarget.fromJson(Map<String, Object?> json) => _$DeviceBootstrapCodeTargetFromJson(json);
}
