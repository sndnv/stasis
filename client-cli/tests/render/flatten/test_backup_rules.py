import unittest

from client_cli.render.flatten.backup_rules import (
    get_spec_rules,
    get_spec_matched,
    get_spec_unmatched,
    flatten_rules,
    flatten_specification_matched,
    flatten_specification_unmatched,
    transform_specification_explanation
)
from tests.mocks import mock_data


class BackupRulesSpec(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.expected_rules_keys = ['operation', 'directory', 'pattern', 'original_line_number']
        cls.expected_matched_keys = ['state', 'entity', 'explanation']
        cls.expected_unmatched_keys = ['line', 'rule', 'failure']

    def test_should_retrieve_backup_rules_spec(self):
        spec = get_spec_rules()

        for key in self.expected_rules_keys:
            self.assertIn(key, spec['fields'])

        self.assertIn('field', spec['sorting'])
        self.assertIn('ordering', spec['sorting'])

    def test_should_retrieve_matched_backup_specification_spec(self):
        spec = get_spec_matched()

        for key in self.expected_matched_keys:
            self.assertIn(key, spec['fields'])

        self.assertIn('field', spec['sorting'])
        self.assertIn('ordering', spec['sorting'])

    def test_should_retrieve_unmatched_backup_specification_spec(self):
        spec = get_spec_unmatched()

        for key in self.expected_unmatched_keys:
            self.assertIn(key, spec['fields'])

        self.assertIn('field', spec['sorting'])
        self.assertIn('ordering', spec['sorting'])

    def test_should_flatten_backup_rules(self):
        for rule in flatten_rules(rules=mock_data.BACKUP_RULES['default']):
            for key in self.expected_rules_keys:
                self.assertIn(key, rule)
                self.assertFalse(isinstance(rule[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(rule[key], list), 'for key [{}]'.format(key))

    def test_should_flatten_specification_matched_and_included_backup_rules(self):
        for rule in flatten_specification_matched(state='included', spec=mock_data.BACKUP_SPEC):
            for key in self.expected_matched_keys:
                self.assertIn(key, rule)
                self.assertFalse(isinstance(rule[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(rule[key], list), 'for key [{}]'.format(key))

    def test_should_flatten_matched_and_excluded_backup_rules(self):
        for rule in flatten_specification_matched(state='excluded', spec=mock_data.BACKUP_SPEC):
            for key in self.expected_matched_keys:
                self.assertIn(key, rule)
                self.assertFalse(isinstance(rule[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(rule[key], list), 'for key [{}]'.format(key))

    def test_should_flatten_unmatched_backup_rules(self):
        for rule in flatten_specification_unmatched(spec=mock_data.BACKUP_SPEC):
            for key in self.expected_unmatched_keys:
                self.assertIn(key, rule)
                self.assertFalse(isinstance(rule[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(rule[key], list), 'for key [{}]'.format(key))

    def test_should_flatten_specification_explanations(self):
        explanation1 = [{'operation': 'include', 'original': {'line': '+ /some/path *', 'line_number': 0}}]
        explanation2 = [{'operation': 'exclude', 'original': {'line': '- /other   *     # comment', 'line_number': 12}}]

        self.assertEqual(
            transform_specification_explanation(explanation=explanation1),
            '(include @ line   0): + /some/path *'
        )
        self.assertEqual(
            transform_specification_explanation(explanation=explanation2),
            '(exclude @ line  12): - /other   *'
        )
        self.assertEqual(
            transform_specification_explanation(explanation=None),
            '<required>'
        )
