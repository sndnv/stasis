import 'package:freezed_annotation/freezed_annotation.dart';

part 'operation_started.freezed.dart';
part 'operation_started.g.dart';

@freezed
abstract class OperationStarted with _$OperationStarted {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory OperationStarted({
    required String operation,
  }) = _OperationStarted;

  factory OperationStarted.fromJson(Map<String, Object?> json) => _$OperationStartedFromJson(json);
}
