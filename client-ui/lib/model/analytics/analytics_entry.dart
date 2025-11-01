import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:stasis_client_ui/model/formats.dart';

part 'analytics_entry.freezed.dart';

part 'analytics_entry.g.dart';

@freezed
abstract class AnalyticsEntry with _$AnalyticsEntry {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory AnalyticsEntry({
    required RuntimeInformation runtime,
    required List<Event> events,
    required List<Failure> failures,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime created,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime updated,
  }) = _AnalyticsEntry;

  factory AnalyticsEntry.fromJson(Map<String, Object?> json) => _$AnalyticsEntryFromJson(json);
}

@freezed
abstract class RuntimeInformation with _$RuntimeInformation {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory RuntimeInformation({
    required String id,
    required String app,
    required String jre,
    required String os,
  }) = _RuntimeInformation;

  factory RuntimeInformation.fromJson(Map<String, Object?> json) => _$RuntimeInformationFromJson(json);
}

@freezed
abstract class Event with _$Event {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Event({
    required int id,
    required String event,
  }) = _Event;

  factory Event.fromJson(Map<String, Object?> json) => _$EventFromJson(json);
}

@freezed
abstract class Failure with _$Failure {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Failure({
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime timestamp,
    required String message,
  }) = _Failure;

  factory Failure.fromJson(Map<String, Object?> json) => _$FailureFromJson(json);
}
