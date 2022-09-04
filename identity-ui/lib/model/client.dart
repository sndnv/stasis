import 'package:freezed_annotation/freezed_annotation.dart';

part 'client.freezed.dart';
part 'client.g.dart';

@freezed
class Client with _$Client {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Client({
    required String id,
    required String redirectUri,
    required int tokenExpiration,
    required bool active,
    String? subject,
  }) = _Client;

  factory Client.fromJson(Map<String, Object?> json) => _$ClientFromJson(json);
}
