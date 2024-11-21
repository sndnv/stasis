import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:stasis_client_ui/model/operations/rule.dart';

void main() {
  group('Rule should', () {
    test('support loading rules from JSON', () {
      const jsonIncludeRule = '''
        {
          "operation": "include",
          "directory": "/some/path",
          "pattern": "*",
          "original": {"line": "+ /some/path *", "line_number": 0}
        }
      ''';

      const jsonExcludeRule = '''
        {
          "operation": "exclude",
          "directory": "/",
          "pattern": "other",
          "comment": "Some comment",
          "original": {"line": "- / other # Some comment", "line_number": 1}
        }
      ''';

      const expectedIncludeRule = Rule(
        operation: 'include',
        directory: '/some/path',
        pattern: '*',
        comment: null,
        original: OriginalRule(line: '+ /some/path *', lineNumber: 0),
      );

      const expectedExcludeRule = Rule(
        operation: 'exclude',
        directory: '/',
        pattern: 'other',
        comment: 'Some comment',
        original: OriginalRule(line: '- / other # Some comment', lineNumber: 1),
      );

      final actualIncludeRule = Rule.fromJson(jsonDecode(jsonIncludeRule));
      final actualExcludeRule = Rule.fromJson(jsonDecode(jsonExcludeRule));

      expect(expectedIncludeRule, actualIncludeRule);
      expect(expectedExcludeRule, actualExcludeRule);
    });
  });
}
