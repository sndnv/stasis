import 'package:stasis_client_ui/utils/triple.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('A Triple should', () {
    test('support comparing triples', () async {
      expect(Triple(1, 2, 3) == Triple(1, 2, 3), true);
      expect(Triple(1, 2, 3) == Triple(3, 4, 5), false);
      expect(Triple('a', 2, true) == Triple('a', 2, true), true);
    });

    test('support converting triples to strings', () async {
      expect(Triple(1, 2, true).toString(), '(1,2,true)');
      expect(Triple('a', 2, true).toString(), '(a,2,true)');
    });
  });
}
