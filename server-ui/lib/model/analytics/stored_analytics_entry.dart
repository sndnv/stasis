import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/analytics/analytics_entry_event.dart';
import 'package:server_ui/model/analytics/analytics_entry_failure.dart';
import 'package:server_ui/model/analytics/analytics_entry_runtime_information.dart';
import 'package:server_ui/model/formats.dart';

part 'stored_analytics_entry.freezed.dart';
part 'stored_analytics_entry.g.dart';

@freezed
abstract class StoredAnalyticsEntry with _$StoredAnalyticsEntry {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory StoredAnalyticsEntry({
    required String id,
    required AnalyticsEntryRuntimeInformation runtime,
    required List<AnalyticsEntryEvent> events,
    required List<AnalyticsEntryFailure> failures,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime created,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime updated,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime received,
  }) = _StoredAnalyticsEntry;

  factory StoredAnalyticsEntry.fromJson(Map<String, Object?> json) => _$StoredAnalyticsEntryFromJson(json);
}
