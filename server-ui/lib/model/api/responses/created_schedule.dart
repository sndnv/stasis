import 'package:freezed_annotation/freezed_annotation.dart';

part 'created_schedule.freezed.dart';
part 'created_schedule.g.dart';

@freezed
abstract class CreatedSchedule with _$CreatedSchedule {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreatedSchedule({
    required String schedule,
  }) = _CreatedSchedule;

  factory CreatedSchedule.fromJson(Map<String, Object?> json) => _$CreatedScheduleFromJson(json);
}
