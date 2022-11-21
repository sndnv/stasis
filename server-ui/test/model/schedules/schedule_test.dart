import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/schedules/schedule.dart';

void main() {
  group('A Schedule should', () {
    test('calculate the next invocation date/time', () {
      final now = DateTime.now();

      const interval = Duration(seconds: 10);
      const startOffset = 2.5;

      final pastSchedule = Schedule(
        id: 'test-schedule-id',
        info: 'test-schedule-info',
        isPublic: true,
        start: now.subtract(interval * startOffset),
        interval: interval,
      );

      final recentSchedule = pastSchedule.copyWith(start: now);

      final futureSchedule = pastSchedule.copyWith(start: now.add(interval * startOffset));

      expect(
        pastSchedule.nextInvocation(),
        pastSchedule.start.add(interval * (startOffset + 1).toInt()),
      );

      expect(
        recentSchedule.nextInvocation(),
        recentSchedule.start.add(recentSchedule.interval),
      );

      expect(
        futureSchedule.nextInvocation(),
        futureSchedule.start,
      );
    });
  });
}
