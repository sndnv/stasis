import 'package:freezed_annotation/freezed_annotation.dart';

part 'rule.freezed.dart';
part 'rule.g.dart';

@freezed
abstract class Rule with _$Rule {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Rule({
    required String operation,
    required String directory,
    required String pattern,
    String? comment,
    required OriginalRule original,
  }) = _Rule;

  factory Rule.fromJson(Map<String, Object?> json) => _$RuleFromJson(json);
}

@freezed
abstract class OriginalRule with _$OriginalRule {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory OriginalRule({
    required String line,
    required int lineNumber,
  }) = _OriginalRule;

  factory OriginalRule.fromJson(Map<String, Object?> json) => _$OriginalRuleFromJson(json);
}
