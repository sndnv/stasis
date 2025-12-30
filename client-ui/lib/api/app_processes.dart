import 'dart:collection';
import 'dart:convert';
import 'dart:io';

import 'package:collection/collection.dart';

class AppProcesses {
  const AppProcesses({
    required this.serviceBinary,
    required this.serviceMainClass,
  });

  final String serviceBinary;
  final String serviceMainClass;

  Future<List<int>> get() async {
    final pidExtractor = RegExp(r'^(\d+)\s+.*');

    final result = await Process.run('ps', ['-Ao', 'pid,command'], stdoutEncoding: utf8);

    final processes = (result.stdout as String)
        .split('\n')
        .map((line) => line.trim())
        .where((line) => line.contains(serviceMainClass))
        .expand((line) {
          final pid = int.tryParse(pidExtractor.firstMatch(line.trim())?.group(1)?.trim() ?? '');
          return pid != null ? [pid] : <int>[];
        });

    return processes.toList();
  }

  void stop(List<int> processes) {
    for (final pid in processes) {
      Process.killPid(pid);
    }
  }

  Future<void> start() async {
    await Process.start(serviceBinary, [], mode: ProcessStartMode.detached, runInShell: true);
  }

  Future<void> regenerateApiCertificate({
    required bool enableDebugging,
  }) async {
    final args = ['maintenance', 'regenerate-api-certificate'];

    await Process.start(
      serviceBinary,
      args,
      mode: ProcessStartMode.normal,
      runInShell: true,
      environment: {
        'STASIS_CLIENT_LOGLEVEL': enableDebugging ? 'DEBUG' : 'INFO',
        'STASIS_CLIENT_LOG_TARGET': 'CONSOLE',
      },
    ).then((underlying) async {
      final process = ExtendedProcess(process: underlying);

      await process.expectExists();

      await process.expect('Generating a new client API certificate');

      final boostrapResult = await process.expectOneOf([
        'Client API certificate generation completed',
        'Client startup failed',
      ]);

      await process.expectEnd();

      if (boostrapResult != 0) {
        throw ProcessExpectationFailure(
          message: 'Client operation failed',
          stdout: process.stdout,
          stderr: process.stderr,
        );
      }
    });
  }

  Future<void> resetUserCredentials({
    required String currentPassword,
    required String newPassword,
    required String newSalt,
    required bool enableDebugging,
  }) async {
    final args = ['maintenance', 'credentials', 'reset'];

    await Process.start(
      serviceBinary,
      args,
      mode: ProcessStartMode.normal,
      runInShell: true,
      environment: {
        'STASIS_CLIENT_LOGLEVEL': enableDebugging ? 'DEBUG' : 'INFO',
        'STASIS_CLIENT_LOG_TARGET': 'CONSOLE',
        'STASIS_CLIENT_RESET_USER_CREDENTIALS_CURRENT_USER_PASSWORD': currentPassword,
        'STASIS_CLIENT_RESET_USER_CREDENTIALS_NEW_USER_PASSWORD': newPassword,
        'STASIS_CLIENT_RESET_USER_CREDENTIALS_NEW_USER_SALT': newSalt,
      },
    ).then((underlying) async {
      final process = ExtendedProcess(process: underlying);

      await process.expectExists();

      await process.expect('Starting client in maintenance mode');

      final boostrapResult = await process.expectOneOf([
        'Successfully reset credentials for user',
        'Client startup failed',
      ]);

      await process.expectEnd();

      if (boostrapResult != 0) {
        throw ProcessExpectationFailure(
          message: 'Client operation failed',
          stdout: process.stdout,
          stderr: process.stderr,
        );
      }
    });
  }

  Future<void> reEncryptDeviceSecret({
    required String currentUsername,
    required String currentPassword,
    required String oldPassword,
    required bool enableDebugging,
  }) async {
    final args = ['maintenance', 'secret', 're-encrypt'];

    await Process.start(
      serviceBinary,
      args,
      mode: ProcessStartMode.normal,
      runInShell: true,
      environment: {
        'STASIS_CLIENT_LOGLEVEL': enableDebugging ? 'DEBUG' : 'INFO',
        'STASIS_CLIENT_LOG_TARGET': 'CONSOLE',
        'STASIS_CLIENT_REENCRYPT_DEVICE_SECRET_CURRENT_USER_NAME': currentUsername,
        'STASIS_CLIENT_REENCRYPT_DEVICE_SECRET_CURRENT_USER_PASSWORD': currentPassword,
        'STASIS_CLIENT_REENCRYPT_DEVICE_SECRET_OLD_USER_PASSWORD': oldPassword,
      },
    ).then((underlying) async {
      final process = ExtendedProcess(process: underlying);

      await process.expectExists();

      await process.expect('Starting client in maintenance mode');

      final boostrapResult = await process.expectOneOf([
        'Device secret successfully re-encrypted',
        'Client startup failed',
      ]);

      await process.expectEnd();

      if (boostrapResult != 0) {
        throw ProcessExpectationFailure(
          message: 'Client operation failed',
          stdout: process.stdout,
          stderr: process.stderr,
        );
      }
    });
  }

  Future<void> pushDeviceSecret({
    required String currentUsername,
    required String currentPassword,
    required String? remotePassword,
    required bool enableDebugging,
  }) async {
    final args = ['maintenance', 'secret', 'push'];

    await Process.start(
      serviceBinary,
      args,
      mode: ProcessStartMode.normal,
      runInShell: true,
      environment: {
        'STASIS_CLIENT_LOGLEVEL': enableDebugging ? 'DEBUG' : 'INFO',
        'STASIS_CLIENT_LOG_TARGET': 'CONSOLE',
        'STASIS_CLIENT_PUSH_DEVICE_SECRET_CURRENT_USER_NAME': currentUsername,
        'STASIS_CLIENT_PUSH_DEVICE_SECRET_CURRENT_USER_PASSWORD': currentPassword,
      }..addAll(remotePassword != null ? {'STASIS_CLIENT_PUSH_DEVICE_SECRET_REMOTE_PASSWORD': remotePassword} : {}),
    ).then((underlying) async {
      final process = ExtendedProcess(process: underlying);

      await process.expectExists();

      await process.expect('Starting client in maintenance mode');

      final boostrapResult = await process.expectOneOf([
        'Device secret successfully pushed',
        'Client startup failed',
      ]);

      await process.expectEnd();

      if (boostrapResult != 0) {
        throw ProcessExpectationFailure(
          message: 'Client operation failed',
          stdout: process.stdout,
          stderr: process.stderr,
        );
      }
    });
  }

  Future<void> pullDeviceSecret({
    required String currentUsername,
    required String currentPassword,
    required String? remotePassword,
    required bool enableDebugging,
  }) async {
    final args = ['maintenance', 'secret', 'pull'];

    await Process.start(
      serviceBinary,
      args,
      mode: ProcessStartMode.normal,
      runInShell: true,
      environment: {
        'STASIS_CLIENT_LOGLEVEL': enableDebugging ? 'DEBUG' : 'INFO',
        'STASIS_CLIENT_LOG_TARGET': 'CONSOLE',
        'STASIS_CLIENT_PULL_DEVICE_SECRET_CURRENT_USER_NAME': currentUsername,
        'STASIS_CLIENT_PULL_DEVICE_SECRET_CURRENT_USER_PASSWORD': currentPassword,
      }..addAll(remotePassword != null ? {'STASIS_CLIENT_PULL_DEVICE_SECRET_REMOTE_PASSWORD': remotePassword} : {}),
    ).then((underlying) async {
      final process = ExtendedProcess(process: underlying);

      await process.expectExists();

      await process.expect('Starting client in maintenance mode');

      final boostrapResult = await process.expectOneOf([
        'Device secret successfully pulled',
        'Client startup failed',
      ]);

      await process.expectEnd();

      if (boostrapResult != 0) {
        throw ProcessExpectationFailure(
          message: 'Client operation failed',
          stdout: process.stdout,
          stderr: process.stderr,
        );
      }
    });
  }

  Future<void> bootstrap({
    required String server,
    required String code,
    required String username,
    required String password,
    required bool acceptSelfSigned,
    required bool recreateFiles,
    required bool enableDebugging,
  }) async {
    final args =
        ['bootstrap'] +
        (acceptSelfSigned ? ['--accept-self-signed'] : []) +
        (recreateFiles ? ['--recreate-files'] : []);

    await Process.start(
      serviceBinary,
      args,
      mode: ProcessStartMode.normal,
      runInShell: true,
      environment: {
        'STASIS_CLIENT_LOGLEVEL': enableDebugging ? 'DEBUG' : 'INFO',
        'STASIS_CLIENT_LOG_TARGET': 'CONSOLE',
        'STASIS_CLIENT_BOOTSTRAP_SERVER_URL': server,
        'STASIS_CLIENT_BOOTSTRAP_CODE': code,
        'STASIS_CLIENT_BOOTSTRAP_USER_NAME': username,
        'STASIS_CLIENT_BOOTSTRAP_USER_PASSWORD': password,
      },
    ).then((underlying) async {
      final process = ExtendedProcess(process: underlying);

      await process.expectExists();

      await process.expect('Starting client in bootstrap mode');

      final boostrapResult = await process.expectOneOf([
        'Server [$server] successfully processed bootstrap request',
        'Client bootstrap using server [$server] failed',
        'Client startup failed',
      ]);

      await process.expectEnd();

      if (boostrapResult != 0) {
        throw ProcessExpectationFailure(
          message: 'Client bootstrap failed',
          stdout: process.stdout,
          stderr: process.stderr,
        );
      }
    });
  }
}

class ExtendedProcess {
  ExtendedProcess({required this.process, this.timeout = const Duration(seconds: 15)}) {
    process.stdout.transform(utf8.decoder).transform(const LineSplitter()).forEach(_stdout.add);
    process.stderr.transform(utf8.decoder).transform(const LineSplitter()).forEach(_stderr.add);
  }

  final Process process;
  final Duration timeout;

  int _stdoutProgress = 0;
  final Queue<String> _stdout = Queue();
  final Queue<String> _stderr = Queue();

  List<String> get stdout => _stdout.toList();

  List<String> get stderr => _stderr.toList();

  Future<void> expectExists() async {
    final delay = Duration(milliseconds: 100);
    final end = DateTime.now().add(delay * 5);

    do {
      final latest = _stderr.toList();
      final String? matched = latest.firstWhereOrNull(
        (line) {
          final trimmed = line.trim();
          return trimmed.endsWith('not found') || trimmed.endsWith('No such file or directory');
        },
      );
      if (matched != null) {
        throw CommandNotFoundFailure(message: matched);
      } else {
        await Future.delayed(delay);
      }
    } while (end.isAfter(DateTime.now()));
  }

  Future<void> expect(String matcher) async {
    final delay = Duration(milliseconds: (timeout.inMilliseconds / 10).toInt());
    final end = DateTime.now().add(timeout);

    final regex = RegExp(matcher);

    do {
      final latest = _stdout.toList();
      final String? matched = latest.firstWhereIndexedOrNull(
        (i, line) => i >= _stdoutProgress && regex.firstMatch(line) != null,
      );
      if (matched != null) {
        _stdoutProgress = latest.indexOf(matched);
        return;
      } else {
        await Future.delayed(delay);
      }
    } while (end.isAfter(DateTime.now()));

    throw ProcessExpectationFailure(message: 'No match found for [$matcher]', stdout: stdout, stderr: stderr);
  }

  Future<void> expectEnd() async {
    await process.exitCode.timeout(
      timeout,
      onTimeout: () =>
          throw ProcessExpectationFailure(message: 'Process is still active', stdout: stdout, stderr: stderr),
    );
  }

  Future<int> expectOneOf(List<String> matchers) async {
    final delay = Duration(milliseconds: (timeout.inMilliseconds / 10).toInt());
    final end = DateTime.now().add(timeout);

    final regexes = matchers.map((m) => RegExp(m)).toList();

    do {
      final latest = _stdout.toList();
      final String? matched = latest.firstWhereIndexedOrNull(
        (i, line) => i >= _stdoutProgress && regexes.any((m) => m.firstMatch(line) != null),
      );
      if (matched != null) {
        final matcher = regexes.indexWhere((matcher) => matcher.firstMatch(matched) != null);
        _stdoutProgress = latest.indexOf(matched);
        return matcher;
      } else {
        await Future.delayed(delay);
      }
    } while (end.isAfter(DateTime.now()));

    throw ProcessExpectationFailure(
      message: 'No match found for any of [${matchers.join(',')}]',
      stdout: stdout,
      stderr: stderr,
    );
  }

  Future<void> sendline(String string) async {
    await Future.delayed(const Duration(milliseconds: 50));
    process.stdin.writeln(string);
    await Future.delayed(const Duration(milliseconds: 200));
  }
}

class CommandNotFoundFailure implements Exception {
  CommandNotFoundFailure({required this.message});

  final String message;

  @override
  String toString() {
    return message;
  }
}

class ProcessExpectationFailure implements Exception {
  ProcessExpectationFailure({required this.message, required this.stdout, required this.stderr});

  final String message;
  final List<String> stdout;
  final List<String> stderr;

  @override
  String toString() {
    return message;
  }
}
