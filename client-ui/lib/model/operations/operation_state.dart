import 'package:freezed_annotation/freezed_annotation.dart';

part 'operation_state.freezed.dart';
part 'operation_state.g.dart';

abstract class OperationState {
  OperationState();

  factory OperationState.fromJson(Map<String, dynamic> json) {
    final type = json['type'] as String;
    switch (type) {
      case 'backup':
        return BackupState.fromJson(json);
      case 'recovery':
        return RecoveryState.fromJson(json);
      default:
        throw ArgumentError('Unexpected entity type encountered: [$type]');
    }
  }

  Map<String, dynamic> toJson();
}

@freezed
class BackupState extends OperationState with _$BackupState {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory BackupState({
    required String operation,
    required String type,
    required String definition,
    required DateTime started,
    required BackupStateEntities entities,
    required DateTime? metadataCollected,
    required DateTime? metadataPushed,
    required List<String> failures,
    required DateTime? completed,
  }) = _BackupState;

  factory BackupState.fromJson(Map<String, Object?> json) => _$BackupStateFromJson(json);
}

@freezed
class RecoveryState extends OperationState with _$RecoveryState {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory RecoveryState({
    required String operation,
    required String type,
    required DateTime started,
    required RecoveryStateEntities entities,
    required List<String> failures,
    required DateTime? completed,
  }) = _RecoveryState;

  factory RecoveryState.fromJson(Map<String, Object?> json) => _$RecoveryStateFromJson(json);
}

@freezed
class BackupStateEntities with _$BackupStateEntities {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory BackupStateEntities({
    required List<String> discovered,
    required List<String> unmatched,
    required List<String> examined,
    required List<String> collected,
    required Map<String, PendingSourceEntity> pending,
    required Map<String, ProcessedSourceEntity> processed,
    required Map<String, String> failed,
  }) = _BackupStateEntities;

  factory BackupStateEntities.fromJson(Map<String, Object?> json) => _$BackupStateEntitiesFromJson(json);
}

@freezed
class RecoveryStateEntities with _$RecoveryStateEntities {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory RecoveryStateEntities({
    required List<String> examined,
    required List<String> collected,
    required Map<String, PendingTargetEntity> pending,
    required Map<String, ProcessedTargetEntity> processed,
    required List<String> metadataApplied,
    required Map<String, String> failed,
  }) = _RecoveryStateEntities;

  factory RecoveryStateEntities.fromJson(Map<String, Object?> json) => _$RecoveryStateEntitiesFromJson(json);
}

@freezed
class PendingSourceEntity with _$PendingSourceEntity {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory PendingSourceEntity({
    required int expectedParts,
    required int processedParts,
  }) = _PendingSourceEntity;

  factory PendingSourceEntity.fromJson(Map<String, Object?> json) => _$PendingSourceEntityFromJson(json);
}

@freezed
class ProcessedSourceEntity with _$ProcessedSourceEntity {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory ProcessedSourceEntity({
    required int expectedParts,
    required int processedParts,
  }) = _ProcessedSourceEntity;

  factory ProcessedSourceEntity.fromJson(Map<String, Object?> json) => _$ProcessedSourceEntityFromJson(json);
}

@freezed
class PendingTargetEntity with _$PendingTargetEntity {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory PendingTargetEntity({
    required int expectedParts,
    required int processedParts,
  }) = _PendingTargetEntity;

  factory PendingTargetEntity.fromJson(Map<String, Object?> json) => _$PendingTargetEntityFromJson(json);
}

@freezed
class ProcessedTargetEntity with _$ProcessedTargetEntity {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory ProcessedTargetEntity({
    required int expectedParts,
    required int processedParts,
  }) = _ProcessedTargetEntity;

  factory ProcessedTargetEntity.fromJson(Map<String, Object?> json) => _$ProcessedTargetEntityFromJson(json);
}
