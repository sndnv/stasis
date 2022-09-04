import 'package:freezed_annotation/freezed_annotation.dart';

part 'create_client.freezed.dart';
part 'create_client.g.dart';

@freezed
class CreateClient with _$CreateClient {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreateClient({
    required String redirectUri,
    required int tokenExpiration,
    required String rawSecret,
    String? subject,
  }) = _CreateClient;

  factory CreateClient.fromJson(Map<String, Object?> json) => _$CreateClientFromJson(json);
}
