import 'dart:convert';
import 'dart:io';

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
}
