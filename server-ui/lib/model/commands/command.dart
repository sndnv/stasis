import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:server_ui/model/formats.dart';

part 'command.freezed.dart';
part 'command.g.dart';

@freezed
class Command with _$Command {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory Command({
    required int sequenceId,
    required String source,
    String? target,
    required CommandParameters parameters,
    @JsonKey(fromJson: dateTimeFromJson, toJson: dateTimeToJson) required DateTime created,
  }) = _Command;

  factory Command.fromJson(Map<String, Object?> json) => _$CommandFromJson(json);
}

@freezed
class CommandParameters with _$CommandParameters {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CommandParameters({
    required String commandType,
    LogoutUserCommand? logoutUser,
  }) = _CommandParameters;

  factory CommandParameters.fromJson(Map<String, Object?> json) => _$CommandParametersFromJson(json);
}

@freezed
class LogoutUserCommand with _$LogoutUserCommand {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory LogoutUserCommand({
    String? reason,
  }) = _LogoutUserCommand;

  factory LogoutUserCommand.fromJson(Map<String, Object?> json) => _$LogoutUserCommandFromJson(json);
}
