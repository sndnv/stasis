import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:stasis_client_ui/api/app_processes.dart';

void main() {
  group('An ExtendedProcess should', () {
    test('support checking if process exists', () async {
      final process = ExtendedProcess(
        process: await Process.start(
          './test/resources/missing-command.sh',
          [],
          mode: ProcessStartMode.normal,
          runInShell: true,
        ),
        timeout: const Duration(seconds: 5),
      );

      expect(() async => await process.expectExists(), throwsA(const TypeMatcher<CommandNotFoundFailure>()));
    });

    test('support communicating with an active process', () async {
      final process = ExtendedProcess(
        process: await Process.start(
          './test/resources/command.sh',
          [],
          mode: ProcessStartMode.normal,
          runInShell: true,
        ),
        timeout: const Duration(seconds: 5),
      );

      await process.sendline('p1');

      await process.expect('Received param1=p1');
      await process.sendline('p2');
      await process.expect('Received param2=p2');

      await process.sendline('s1');
      await process.sendline('s2');

      final result = await process.expectOneOf([
        r'Command succeeded with \[param1=p1,param2=p2,secret1=s1,secret2=s2\]',
        'Command failed',
      ]);

      expect(result, 0);

      await process.expectEnd();

      expect(process.stdout, [
        'Received param1=p1',
        'Received param2=p2',
        'Command succeeded with [param1=p1,param2=p2,secret1=s1,secret2=s2]',
      ]);

      expect(process.stderr.isEmpty, true);
    });

    test('support handline process failures', () async {
      final process = ExtendedProcess(
        process: await Process.start(
          './test/resources/command.sh',
          ['fail'],
          mode: ProcessStartMode.normal,
          runInShell: true,
        ),
        timeout: const Duration(seconds: 5),
      );

      await process.sendline('p1');

      await process.expect('Received param1=p1');

      final result = await process.expectOneOf([
        r'Command succeeded with \[param1=p1,param2=p2,secret1=s1,secret2=s2\]',
        'Command failed',
        'Other',
      ]);

      expect(result, 1);

      await process.expectEnd();

      expect(process.stdout, ['Received param1=p1', 'Command failed']);

      expect(process.stderr.isEmpty, true);
    });
  });
}
