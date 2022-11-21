import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/formats.dart';

void main() {
  group('Formats should', () {
    test('convert durations to/from JSON', () async {
      const original = Duration(days: 3, minutes: 15);
      final json = durationToJson(original);

      expect(json, 260100);
      expect(durationFromJson(json), original);
    });

    test('convert DateTimes to/from JSON', () async {
      final original = DateTime(2020, 12, 31, 23, 45, 30);
      final json = dateTimeToJson(original);

      expect(json, '2020-12-31T23:45:30.000');
      expect(dateTimeFromJson(json), original);
    });
  });
}
