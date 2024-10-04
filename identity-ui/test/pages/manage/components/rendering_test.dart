import 'package:flutter_test/flutter_test.dart';
import 'package:identity_ui/pages/manage/components/rendering.dart';

void main() {
  group('Rendering components should', () {
    test('convert DateTimes to strings', () {
      final timestamp = DateTime(2020, 12, 31, 23, 45, 30);

      expect(timestamp.render(), '2020-12-31 23:45');
      expect(timestamp.renderAsDate(), '2020-12-31');
      expect(timestamp.renderAsTime(), '23:45');
    });
  });
}
