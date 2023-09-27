import 'dart:convert';

import 'package:stasis_client_ui/model/schedules/active_schedule.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('An ActiveSchedule Assignment should', () {
    test('support loading active backup assignments from JSON', () {
      const assignmentJson = '''
        {
          "assignment_type": "backup",
          "schedule": "test-schedule",
          "definition": "test-definition",
          "entities": ["a", "b", "c"]
        }
      ''';

      const expectedAssignment = BackupAssignment(
        assignmentType: 'backup',
        schedule: 'test-schedule',
        definition: 'test-definition',
        entities: ['a', 'b', 'c'],
      );

      final actualAssignment = Assignment.fromJson(jsonDecode(assignmentJson));

      expect(expectedAssignment, actualAssignment);
    });

    test('support loading active expiration assignments from JSON', () {
      const assignmentJson = '''
        {
          "assignment_type": "expiration",
          "schedule": "test-schedule"
        }
      ''';

      const expectedAssignment = ExpirationAssignment(
        assignmentType: 'expiration',
        schedule: 'test-schedule',
      );

      final actualAssignment = Assignment.fromJson(jsonDecode(assignmentJson));

      expect(expectedAssignment, actualAssignment);
    });

    test('support loading active validation assignments from JSON', () {
      const assignmentJson = '''
        {
          "assignment_type": "validation",
          "schedule": "test-schedule"
        }
      ''';

      const expectedAssignment = ValidationAssignment(
        assignmentType: 'validation',
        schedule: 'test-schedule',
      );

      final actualAssignment = Assignment.fromJson(jsonDecode(assignmentJson));

      expect(expectedAssignment, actualAssignment);
    });

    test('support loading active key rotation assignments from JSON', () {
      const assignmentJson = '''
        {
          "assignment_type": "key-rotation",
          "schedule": "test-schedule"
        }
      ''';

      const expectedAssignment = KeyRotationAssignment(
        assignmentType: 'key-rotation',
        schedule: 'test-schedule',
      );

      final actualAssignment = Assignment.fromJson(jsonDecode(assignmentJson));

      expect(expectedAssignment, actualAssignment);
    });

    test('fail to load assignments with invalid types', () {
      expect(() => Assignment.fromJson(jsonDecode('{"assignment_type":"other"}')), throwsArgumentError);
    });
  });
}
