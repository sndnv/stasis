import 'package:stasis_client_ui/model/operations/operation.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'operation_progress.freezed.dart';

part 'operation_progress.g.dart';

@freezed
class OperationProgress with _$OperationProgress {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory OperationProgress({
    required String operation,
    required bool isActive,
    @JsonKey(fromJson: Type.fromName, toJson: Type.toName) required Type type,
    required Progress progress,
  }) = _OperationProgress;

  factory OperationProgress.fromJson(Map<String, Object?> json) => _$OperationProgressFromJson(json);
}
