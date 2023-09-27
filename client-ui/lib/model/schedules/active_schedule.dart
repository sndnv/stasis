import 'package:stasis_client_ui/model/formats.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'active_schedule.freezed.dart';
part 'active_schedule.g.dart';

@freezed
class ActiveSchedule with _$ActiveSchedule {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory ActiveSchedule({
    required Assignment assignment,
    required EmbeddedSchedule? schedule,
  }) = _ActiveSchedule;

  factory ActiveSchedule.fromJson(Map<String, Object?> json) => _$ActiveScheduleFromJson(json);
}

abstract class Assignment {
  Assignment();

  factory Assignment.fromJson(Map<String, dynamic> json) {
    final type = json['assignment_type'] as String;
    switch (type) {
      case 'backup':
        return BackupAssignment.fromJson(json);
      case 'expiration':
        return ExpirationAssignment.fromJson(json);
      case 'validation':
        return ValidationAssignment.fromJson(json);
      case 'key-rotation':
        return KeyRotationAssignment.fromJson(json);
      default:
        throw ArgumentError('Unexpected assignment type encountered: [$type]');
    }
  }

  Map<String, dynamic> toJson();
}

@freezed
class BackupAssignment extends Assignment with _$BackupAssignment {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory BackupAssignment({
    required String assignmentType,
    required String schedule,
    required String definition,
    required List<String> entities,
  }) = _BackupAssignment;

  factory BackupAssignment.fromJson(Map<String, Object?> json) => _$BackupAssignmentFromJson(json);
}

@freezed
class ExpirationAssignment extends Assignment with _$ExpirationAssignment {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory ExpirationAssignment({
    required String assignmentType,
    required String schedule,
  }) = _ExpirationAssignment;

  factory ExpirationAssignment.fromJson(Map<String, Object?> json) => _$ExpirationAssignmentFromJson(json);
}

@freezed
class ValidationAssignment extends Assignment with _$ValidationAssignment {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory ValidationAssignment({
    required String assignmentType,
    required String schedule,
  }) = _ValidationAssignment;

  factory ValidationAssignment.fromJson(Map<String, Object?> json) => _$ValidationAssignmentFromJson(json);
}

@freezed
class KeyRotationAssignment extends Assignment with _$KeyRotationAssignment {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory KeyRotationAssignment({
    required String assignmentType,
    required String schedule,
  }) = _KeyRotationAssignment;

  factory KeyRotationAssignment.fromJson(Map<String, Object?> json) => _$KeyRotationAssignmentFromJson(json);
}

@freezed
class EmbeddedSchedule with _$EmbeddedSchedule {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory EmbeddedSchedule({
    required String id,
    required String info,
    required bool isPublic,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime start,
    @JsonKey(fromJson: durationFromJson, toJson: durationToJson) required Duration interval,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime nextInvocation,
    required String retrieval,
    required String? message,
  }) = _EmbeddedSchedule;

  factory EmbeddedSchedule.fromJson(Map<String, Object?> json) => _$EmbeddedScheduleFromJson(json);
}
