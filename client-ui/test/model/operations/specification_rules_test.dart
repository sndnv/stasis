import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:stasis_client_ui/model/operations/rule.dart';
import 'package:stasis_client_ui/model/operations/specification_rules.dart';
import 'package:stasis_client_ui/utils/pair.dart';

void main() {
  group('SpecificationRules should', () {
    test('support loading rules from JSON', () {
      const rulesJson = '''
        {
            "included": ["/some/path/01", "/some/path", "/some"],
            "excluded": ["/other"],
            "explanation": {
                "/some/path/01": [{"operation": "include", "original": {"line": "+ /some/path *", "line_number": 0}}],
                "/other": [{"operation": "exclude", "original": {"line": "- / other", "line_number": 1}}]
            },
            "unmatched": [
                [{"line": "+ /test_01 *", "line_number": 2}, "Not found"],
                [{"line": "- /test_02 *", "line_number": 3}, "Test failure"]
            ]
        }
      ''';

      final expectedRules = SpecificationRules(
        included: ['/some/path/01', '/some/path', '/some'],
        excluded: ['/other'],
        explanation: {
          '/some/path/01': [
            const Explanation(operation: 'include', original: OriginalRule(line: '+ /some/path *', lineNumber: 0)),
          ],
          '/other': [
            const Explanation(operation: 'exclude', original: OriginalRule(line: '- / other', lineNumber: 1)),
          ],
        },
        unmatched: [
          Pair(const OriginalRule(line: '+ /test_01 *', lineNumber: 2), 'Not found'),
          Pair(const OriginalRule(line: '- /test_02 *', lineNumber: 3), 'Test failure'),
        ],
      );

      final actualRules = SpecificationRules.fromJson(jsonDecode(rulesJson));

      expect(expectedRules, actualRules);
    });
  });
}
