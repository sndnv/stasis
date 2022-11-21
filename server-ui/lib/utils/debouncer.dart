import 'dart:async';

class Debouncer {
  Debouncer({required this.timeout});

  final Duration timeout;
  Timer? _timer;

  void run(void Function() action) {
    _timer?.cancel();
    _timer = Timer(timeout, action);
  }
}
