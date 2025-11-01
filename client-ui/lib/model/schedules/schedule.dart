import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:stasis_client_ui/model/formats.dart';

part 'schedule.freezed.dart';
part 'schedule.g.dart';

@freezed
abstract class Schedule with _$Schedule {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Schedule({
    required String id,
    required String info,
    required bool isPublic,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime start,
    @JsonKey(fromJson: durationFromJson, toJson: durationToJson) required Duration interval,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime nextInvocation,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime created,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime updated,
  }) = _Schedule;

  factory Schedule.fromJson(Map<String, Object?> json) => _$ScheduleFromJson(json);
}
