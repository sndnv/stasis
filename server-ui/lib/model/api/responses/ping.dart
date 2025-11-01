import 'package:freezed_annotation/freezed_annotation.dart';

part 'ping.freezed.dart';
part 'ping.g.dart';

@freezed
abstract class Ping with _$Ping {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Ping({
    required String id,
  }) = _Ping;

  factory Ping.fromJson(Map<String, Object?> json) => _$PingFromJson(json);
}
