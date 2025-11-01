import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:stasis_client_ui/model/formats.dart';

part 'dataset_entry.freezed.dart';
part 'dataset_entry.g.dart';

@freezed
abstract class DatasetEntry with _$DatasetEntry {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory DatasetEntry({
    required String id,
    required String definition,
    required String device,
    required Set<String> data,
    required String metadata,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime created,
  }) = _DatasetEntry;

  factory DatasetEntry.fromJson(Map<String, Object?> json) => _$DatasetEntryFromJson(json);
}
