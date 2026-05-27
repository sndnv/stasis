import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/api/requests/create_schedule.dart';
import 'package:server_ui/model/api/requests/update_schedule.dart';
import 'package:server_ui/model/api/responses/created_schedule.dart';
import 'package:server_ui/model/schedules/schedule.dart';

import '../assertions.dart';

void main() {
  group('Schedule interop', () {
    test('decode and re-encode a schedule', () {
      assertInterop(
        domain: 'schedules',
        resource: 'Schedule',
        matches: Schedule(
          id: 'e4e1f2a7-ff2d-4e8f-b715-9bbe607684d4',
          info: 'nightly backup',
          isPublic: true,
          start: DateTime.parse('2026-05-01T02:00:30'),
          interval: const Duration(seconds: 86400),
          created: DateTime.parse('2026-05-01T09:00:00Z'),
          updated: DateTime.parse('2026-05-02T09:00:00Z'),
        ),
        fromJson: Schedule.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode CreateSchedule', () {
      assertInterop(
        domain: 'schedules',
        resource: 'CreateSchedule',
        matches: CreateSchedule(
          info: 'nightly backup',
          isPublic: true,
          start: DateTime.parse('2026-05-01T02:00:30'),
          interval: const Duration(seconds: 86400),
        ),
        fromJson: CreateSchedule.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode UpdateSchedule', () {
      assertInterop(
        domain: 'schedules',
        resource: 'UpdateSchedule',
        matches: UpdateSchedule(
          info: 'updated nightly backup',
          start: DateTime.parse('2026-06-01T03:00:30'),
          interval: const Duration(seconds: 43200),
        ),
        fromJson: UpdateSchedule.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode CreatedSchedule', () {
      assertInterop(
        domain: 'schedules',
        resource: 'CreatedSchedule',
        matches: const CreatedSchedule(schedule: 'b8164e47-5ff7-4f99-b0b4-f72a4cd5b792'),
        fromJson: CreatedSchedule.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
