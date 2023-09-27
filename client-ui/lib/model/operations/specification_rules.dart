import 'package:stasis_client_ui/utils/pair.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'specification_rules.freezed.dart';
part 'specification_rules.g.dart';

@freezed
class SpecificationRules with _$SpecificationRules {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory SpecificationRules({
    required List<String> included,
    required List<String> excluded,
    required Map<String, List<Explanation>> explanation,
    @JsonKey(fromJson: unmatchedFromJson, toJson: unmatchedToJson) required List<Pair<Original, String>> unmatched,
  }) = _SpecificationRules;

  factory SpecificationRules.fromJson(Map<String, Object?> json) => _$SpecificationRulesFromJson(json);
}

@freezed
class Explanation with _$Explanation {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Explanation({
    required String operation,
    required Original original,
  }) = _Explanation;

  factory Explanation.fromJson(Map<String, Object?> json) => _$ExplanationFromJson(json);
}

@freezed
class Original with _$Original {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Original({
    required String line,
    required int lineNumber,
  }) = _Original;

  factory Original.fromJson(Map<String, Object?> json) => _$OriginalFromJson(json);
}

List<dynamic> unmatchedToJson(List<Pair<Original, String>> unmatched) =>
    unmatched.map((pair) => <dynamic>[pair.a.toJson(), pair.b]).toList();

List<Pair<Original, String>> unmatchedFromJson(List<dynamic> json) => json.map((e) {
      final pair = e as List<dynamic>;
      return Pair(Original.fromJson(pair[0]), pair[1] as String);
    }).toList();
