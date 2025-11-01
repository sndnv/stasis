import 'package:freezed_annotation/freezed_annotation.dart';

part 'created_node.freezed.dart';
part 'created_node.g.dart';

@freezed
abstract class CreatedNode with _$CreatedNode {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CreatedNode({
    required String node,
  }) = _CreatedNode;

  factory CreatedNode.fromJson(Map<String, Object?> json) => _$CreatedNodeFromJson(json);
}
