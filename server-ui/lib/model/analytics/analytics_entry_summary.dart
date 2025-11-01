import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/analytics/analytics_entry_runtime_information.dart';
import 'package:server_ui/model/formats.dart';

part 'analytics_entry_summary.freezed.dart';
part 'analytics_entry_summary.g.dart';

@freezed
abstract class AnalyticsEntrySummary with _$AnalyticsEntrySummary {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory AnalyticsEntrySummary({
    required String id,
    required AnalyticsEntryRuntimeInformation runtime,
    required int events,
    required int failures,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime created,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime updated,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime received,
  }) = _AnalyticsEntrySummary;

  factory AnalyticsEntrySummary.fromJson(Map<String, Object?> json) => _$AnalyticsEntrySummaryFromJson(json);
}
