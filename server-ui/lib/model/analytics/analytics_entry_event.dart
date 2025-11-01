import 'package:freezed_annotation/freezed_annotation.dart';

part 'analytics_entry_event.freezed.dart';
part 'analytics_entry_event.g.dart';

@freezed
abstract class AnalyticsEntryEvent with _$AnalyticsEntryEvent {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory AnalyticsEntryEvent({
    required int id,
    required String event,
  }) = _AnalyticsEntryEvent;

  factory AnalyticsEntryEvent.fromJson(Map<String, Object?> json) => _$AnalyticsEntryEventFromJson(json);
}
