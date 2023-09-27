import 'dart:convert';

import 'package:stasis_client_ui/model/operations/operation_state.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('An OperationState should', () {
    test('support loading backup state from JSON', () {
      final now = DateTime.now();

      final stateJson = '''
        {
          "operation":"test-operation",
          "type":"backup",
          "definition":"test-definition",
          "started":"${now.toString()}",
          "entities":{
            "discovered":["/tmp/file/one"],
            "unmatched":["a","b","c"],
            "examined":["/tmp/file/two"],
            "collected":["/tmp/file/one"],
            "pending":{"/tmp/file/two":{"expected_parts":1,"processed_parts":2}},
            "processed":{"/tmp/file/one":{"expected_parts":1,"processed_parts":1},"/tmp/file/two":{"expected_parts":0,"processed_parts":0}},
            "failed":{"/tmp/file/four":"x"}
          },
          "metadata_collected":"${now.toString()}",
          "metadata_pushed":"${now.toString()}",
          "failures":["y","z"],
          "completed":"${now.toString()}"
        }
      ''';

      final expectedState = BackupState(
        operation: 'test-operation',
        type: 'backup',
        definition: 'test-definition',
        started: now,
        entities: const BackupStateEntities(
          discovered: ['/tmp/file/one'],
          unmatched: ['a', 'b', 'c'],
          examined: ['/tmp/file/two'],
          collected: ['/tmp/file/one'],
          pending: {
            '/tmp/file/two': PendingSourceEntity(expectedParts: 1, processedParts: 2),
          },
          processed: {
            '/tmp/file/one': ProcessedSourceEntity(expectedParts: 1, processedParts: 1),
            '/tmp/file/two': ProcessedSourceEntity(expectedParts: 0, processedParts: 0),
          },
          failed: {
            '/tmp/file/four': 'x',
          },
        ),
        metadataCollected: now,
        metadataPushed: now,
        failures: ['y', 'z'],
        completed: now,
      );

      final actualState = OperationState.fromJson(jsonDecode(stateJson));

      expect(expectedState, actualState);
    });

    test('support loading recovery state from JSON', () {
      final now = DateTime.now();

      final stateJson = '''
        {
          "operation":"test-operation",
          "type":"recovery",
          "definition":"test-definition",
          "started":"${now.toString()}",
          "entities":{
            "examined":["/tmp/file/one","/tmp/file/two","/tmp/file/four"],
            "collected":["/tmp/file/one"],
            "pending":{"/tmp/file/four":{"expected_parts":3,"processed_parts":1}},
            "processed":{"/tmp/file/one":{"expected_parts":1,"processed_parts":1}},
            "metadata_applied":["/tmp/file/one"],
            "failed":{"/tmp/file/four":"x"}
          },
          "failures":["y","z"],
          "completed":"${now.toString()}"
        }
      ''';

      final expectedState = RecoveryState(
        operation: 'test-operation',
        type: 'recovery',
        started: now,
        entities: const RecoveryStateEntities(
          examined: ['/tmp/file/one', '/tmp/file/two', '/tmp/file/four'],
          collected: ['/tmp/file/one'],
          pending: {
            '/tmp/file/four': PendingTargetEntity(expectedParts: 3, processedParts: 1),
          },
          processed: {
            '/tmp/file/one': ProcessedTargetEntity(expectedParts: 1, processedParts: 1),
          },
          metadataApplied: ['/tmp/file/one'],
          failed: {
            '/tmp/file/four': 'x',
          },
        ),
        failures: ['y', 'z'],
        completed: now,
      );

      final actualState = OperationState.fromJson(jsonDecode(stateJson));

      expect(expectedState, actualState);
    });

    test('fail to load state with an invalid entity type', () {
      expect(() => OperationState.fromJson(jsonDecode('{"type":"other"}')), throwsArgumentError);
    });
  });
}
