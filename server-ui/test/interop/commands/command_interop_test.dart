import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/commands/command.dart';

import '../assertions.dart';

void main() {
  group('Command interop', () {
    test('decode and re-encode an empty command', () {
      assertInterop(
        domain: 'commands',
        resource: 'Command.empty',
        matches: Command(
          sequenceId: 0,
          source: 'service',
          parameters: const CommandParameters(commandType: 'empty'),
          created: DateTime.parse('2026-04-15T10:00:00Z'),
        ),
        fromJson: Command.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode a logout-user command', () {
      assertInterop(
        domain: 'commands',
        resource: 'Command.logout-user',
        matches: Command(
          sequenceId: 42,
          source: 'user',
          target: '4e167c18-0808-4f23-886c-598e094a1b6b',
          parameters: const CommandParameters(
            commandType: 'logout_user',
            logoutUser: LogoutUserCommand(reason: 'session timed out'),
          ),
          created: DateTime.parse('2026-04-15T10:00:00Z'),
        ),
        fromJson: Command.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode a logout-user command with no reason', () {
      assertInterop(
        domain: 'commands',
        resource: 'Command.logout-user-no-reason',
        matches: Command(
          sequenceId: 7,
          source: 'service',
          parameters: const CommandParameters(
            commandType: 'logout_user',
            logoutUser: LogoutUserCommand(),
          ),
          created: DateTime.parse('2026-04-15T10:00:00Z'),
        ),
        fromJson: Command.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
