import 'package:freezed_annotation/freezed_annotation.dart';

part 'analytics_entry_runtime_information.freezed.dart';
part 'analytics_entry_runtime_information.g.dart';

@freezed
class AnalyticsEntryRuntimeInformation with _$AnalyticsEntryRuntimeInformation {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory AnalyticsEntryRuntimeInformation({
    required String id,
    required String app,
    required String jre,
    required String os,
  }) = _AnalyticsEntryRuntimeInformation;

  factory AnalyticsEntryRuntimeInformation.fromJson(Map<String, Object?> json) =>
      _$AnalyticsEntryRuntimeInformationFromJson(json);
}
