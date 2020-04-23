import unittest

from client_cli.render.flatten.backup_rules import (
    get_spec_matched,
    get_spec_unmatched,
    flatten_matched,
    flatten_unmatched,
    transform_rule_explanation
)
from tests.mocks import mock_data


class BackupRulesSpec(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.expected_matched_keys = ['state', 'entity', 'explanation']
        cls.expected_unmatched_keys = ['line', 'rule', 'failure']

    def test_should_retrieve_matched_backup_rules_spec(self):
        spec = get_spec_matched()

        for key in self.expected_matched_keys:
            self.assertIn(key, spec['fields'])

        self.assertIn('field', spec['sorting'])
        self.assertIn('ordering', spec['sorting'])

    def test_should_retrieve_unmatched_backup_rules_spec(self):
        spec = get_spec_unmatched()

        for key in self.expected_unmatched_keys:
            self.assertIn(key, spec['fields'])

        self.assertIn('field', spec['sorting'])
        self.assertIn('ordering', spec['sorting'])

    def test_should_flatten_matched_and_included_backup_rules(self):
        for rule in flatten_matched(state='included', rules=mock_data.BACKUP_RULES):
            for key in self.expected_matched_keys:
                self.assertIn(key, rule)
                self.assertFalse(isinstance(rule[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(rule[key], list), 'for key [{}]'.format(key))

    def test_should_flatten_matched_and_excluded_backup_rules(self):
        for rule in flatten_matched(state='excluded', rules=mock_data.BACKUP_RULES):
            for key in self.expected_matched_keys:
                self.assertIn(key, rule)
                self.assertFalse(isinstance(rule[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(rule[key], list), 'for key [{}]'.format(key))

    def test_should_flatten_unmatched_backup_rules(self):
        for rule in flatten_unmatched(rules=mock_data.BACKUP_RULES):
            for key in self.expected_unmatched_keys:
                self.assertIn(key, rule)
                self.assertFalse(isinstance(rule[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(rule[key], list), 'for key [{}]'.format(key))

    def test_should_flatten_rule_explanations(self):
        explanation1 = [{'operation': 'include', 'original': {'line': '+ /some/path *', 'line_number': 0}}]
        explanation2 = [{'operation': 'exclude', 'original': {'line': '- /other   *     # comment', 'line_number': 12}}]

        self.assertEqual(transform_rule_explanation(explanation=explanation1), '(include @ line   0): + /some/path *')
        self.assertEqual(transform_rule_explanation(explanation=explanation2), '(exclude @ line  12): - /other   *')
        self.assertEqual(transform_rule_explanation(explanation=None), '<required>')
