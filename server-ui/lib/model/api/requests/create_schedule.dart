import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/formats.dart';

part 'create_schedule.freezed.dart';
part 'create_schedule.g.dart';

@freezed
class CreateSchedule with _$CreateSchedule {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreateSchedule({
    required String info,
    required bool isPublic,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime start,
    @JsonKey(fromJson: durationFromJson, toJson: durationToJson) required Duration interval,
  }) = _CreateSchedule;

  factory CreateSchedule.fromJson(Map<String, Object?> json) => _$CreateScheduleFromJson(json);
}
