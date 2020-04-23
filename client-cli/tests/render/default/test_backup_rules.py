import unittest

from client_cli.render.default.backup_rules import render_matched_rules_as_table, render_unmatched_rules_as_table
from client_cli.render.flatten.backup_rules import flatten_matched, flatten_unmatched
from tests.mocks import mock_data


class BackupRulesAsTableSpec(unittest.TestCase):

    def test_should_render_matched_rules_as_table(self):
        header_size = 3  # header border (x2) + header
        footer_size = 3  # footer border (x2) + footer

        table = render_matched_rules_as_table(
            state='included',
            rules=flatten_matched(state='included', rules=mock_data.BACKUP_RULES)
        )
        self.assertEqual(len(table.split('\n')), header_size + len(mock_data.BACKUP_RULES['included']) + footer_size)

        table = render_matched_rules_as_table(
            state='excluded',
            rules=flatten_matched(state='excluded', rules=mock_data.BACKUP_RULES)
        )
        self.assertEqual(len(table.split('\n')), header_size + len(mock_data.BACKUP_RULES['excluded']) + footer_size)

    def test_should_render_unmatched_rules_as_table(self):
        header_size = 3  # header border (x2) + header
        footer_size = 1  # footer border

        table = render_unmatched_rules_as_table(rules=flatten_unmatched(rules=mock_data.BACKUP_RULES))
        self.assertEqual(len(table.split('\n')), header_size + len(mock_data.BACKUP_RULES['unmatched']) + footer_size)

    def test_should_render_a_message_when_no_matched_rules_are_available(self):
        result = render_matched_rules_as_table(state='included', rules=[])
        self.assertEqual(result, 'No data')

        result = render_matched_rules_as_table(state='excluded', rules=[])
        self.assertEqual(result, 'No data')

    def test_should_render_a_message_when_no_unmatched_rules_are_available(self):
        result = render_unmatched_rules_as_table(rules=[])
        self.assertEqual(result, 'No data')
