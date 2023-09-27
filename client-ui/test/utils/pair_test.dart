import 'package:stasis_client_ui/utils/pair.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('A Pair should', () {
    test('support comparing pairs', () async {
      expect(Pair(1, 2) == Pair(1, 2), true);
      expect(Pair(1, 2) == Pair(3, 4), false);
      expect(Pair('a', 2) == Pair('a', 2), true);
    });

    test('support converting pairs to strings', () async {
      expect(Pair(1, 2).toString(), '(1,2)');
      expect(Pair('a', 2).toString(), '(a,2)');
    });
  });
}
