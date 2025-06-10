import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/formats.dart';

part 'analytics_entry_failure.freezed.dart';
part 'analytics_entry_failure.g.dart';

@freezed
class AnalyticsEntryFailure with _$AnalyticsEntryFailure {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory AnalyticsEntryFailure({
    required String message,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime timestamp,
  }) = _AnalyticsEntryFailure;

  factory AnalyticsEntryFailure.fromJson(Map<String, Object?> json) => _$AnalyticsEntryFailureFromJson(json);
}
