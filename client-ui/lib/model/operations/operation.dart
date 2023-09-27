import 'package:freezed_annotation/freezed_annotation.dart';

part 'operation.freezed.dart';
part 'operation.g.dart';

enum State {
  active(name: 'active'),
  completed(name: 'completed'),
  all(name: 'all');

  const State({required this.name});

  final String name;

  static State fromName(String name) {
    switch (name) {
      case 'active':
        return State.active;
      case 'completed':
        return State.completed;
      case 'all':
        return State.all;
      default:
        throw ArgumentError('Unexpected operation state encountered: [$name]');
    }
  }
}

enum Type {
  backup(name: 'client-backup'),
  recovery(name: 'client-recovery'),
  expiration(name: 'client-expiration'),
  validation(name: 'client-validation'),
  keyRotation(name: 'client-key-rotation'),
  garbageCollection(name: 'server-garbage-collection');

  const Type({required this.name});

  final String name;

  static String toName(Type type) {
    return type.name;
  }

  static Type fromName(String name) {
    switch (name) {
      case 'client-backup':
        return Type.backup;
      case 'client-recovery':
        return Type.recovery;
      case 'client-expiration':
        return Type.expiration;
      case 'client-validation':
        return Type.validation;
      case 'client-key-rotation':
        return Type.keyRotation;
      case 'server-garbage-collection':
        return Type.garbageCollection;
      default:
        throw ArgumentError('Unexpected operation type encountered: [$name]');
    }
  }
}

@freezed
class Progress with _$Progress {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Progress({
    required DateTime started,
    required int total,
    required int processed,
    required int failures,
    required DateTime? completed,
  }) = _Progress;

  factory Progress.fromJson(Map<String, Object?> json) => _$ProgressFromJson(json);
}

