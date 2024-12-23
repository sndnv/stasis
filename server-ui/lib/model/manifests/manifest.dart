import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/formats.dart';

part 'manifest.freezed.dart';
part 'manifest.g.dart';

@freezed
class Manifest with _$Manifest {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Manifest({
    required String crate,
    required int size,
    required int copies,
    required String origin,
    required String source,
    required List<String> destinations,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime created,
  }) = _Manifest;

  factory Manifest.fromJson(Map<String, Object?> json) => _$ManifestFromJson(json);
}
