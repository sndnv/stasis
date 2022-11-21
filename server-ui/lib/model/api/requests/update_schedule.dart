import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/formats.dart';

part 'update_schedule.freezed.dart';
part 'update_schedule.g.dart';

@freezed
class UpdateSchedule with _$UpdateSchedule {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory UpdateSchedule({
    required String info,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime start,
    @JsonKey(fromJson: durationFromJson, toJson: durationToJson) required Duration interval,
  }) = _UpdateSchedule;

  factory UpdateSchedule.fromJson(Map<String, Object?> json) => _$UpdateScheduleFromJson(json);
}
