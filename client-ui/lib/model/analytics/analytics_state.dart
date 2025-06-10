import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:stasis_client_ui/model/analytics/analytics_entry.dart';
import 'package:stasis_client_ui/model/formats.dart';

part 'analytics_state.freezed.dart';
part 'analytics_state.g.dart';

@freezed
class AnalyticsState with _$AnalyticsState {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory AnalyticsState({
    required AnalyticsEntry entry,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime lastCached,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime lastTransmitted,
  }) = _AnalyticsState;

  factory AnalyticsState.fromJson(Map<String, Object?> json) => _$AnalyticsStateFromJson(json);
}
