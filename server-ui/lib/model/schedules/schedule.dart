import 'dart:math';

import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/formats.dart';

part 'schedule.freezed.dart';

part 'schedule.g.dart';

@freezed
class Schedule with _$Schedule {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Schedule({
    required String id,
    required String info,
    required bool isPublic,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime start,
    @JsonKey(fromJson: durationFromJson, toJson: durationToJson) required Duration interval,
  }) = _Schedule;

  factory Schedule.fromJson(Map<String, Object?> json) => _$ScheduleFromJson(json);
}

extension ExtendedSchedule on Schedule {
  DateTime nextInvocation() {
    final now = DateTime.now();

    if (start.isBefore(now)) {
      final intervalMillis = max(interval.inMilliseconds, 1);
      final difference = start.difference(now).abs();

      final int invocations = difference.inMilliseconds ~/ intervalMillis;
      return start.add(Duration(milliseconds: (invocations + 1) * intervalMillis));
    } else {
      return start;
    }
  }
}
