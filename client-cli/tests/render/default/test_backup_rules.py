import unittest

from client_cli.render.default.backup_rules import (
    render_rules_as_table,
    render_matched_specification_as_table,
    render_unmatched_specification_as_table
)
from client_cli.render.flatten.backup_rules import (
    flatten_rules,
    flatten_specification_matched,
    flatten_specification_unmatched
)
from tests.mocks import mock_data


class BackupRulesAsTableSpec(unittest.TestCase):

    def test_should_render_rules_as_table(self):
        header_size = 3  # header border (x2) + header
        footer_size = 3  # footer border (x2) + footer

        table = render_rules_as_table(
            rules=flatten_rules(rules=mock_data.BACKUP_RULES['default'])
        )
        self.assertEqual(len(table.split('\n')), header_size + len(mock_data.BACKUP_RULES['default']) + footer_size)

    def test_should_render_matched_specification_as_table(self):
        header_size = 3  # header border (x2) + header
        footer_size = 3  # footer border (x2) + footer

        table = render_matched_specification_as_table(
            state='included',
            spec=flatten_specification_matched(state='included', spec=mock_data.BACKUP_SPEC)
        )
        self.assertEqual(len(table.split('\n')), header_size + len(mock_data.BACKUP_SPEC['included']) + footer_size)

        table = render_matched_specification_as_table(
            state='excluded',
            spec=flatten_specification_matched(state='excluded', spec=mock_data.BACKUP_SPEC)
        )
        self.assertEqual(len(table.split('\n')), header_size + len(mock_data.BACKUP_SPEC['excluded']) + footer_size)

    def test_should_render_unmatched_specification_as_table(self):
        header_size = 3  # header border (x2) + header
        footer_size = 1  # footer border

        table = render_unmatched_specification_as_table(
            spec=flatten_specification_unmatched(spec=mock_data.BACKUP_SPEC)
        )
        self.assertEqual(len(table.split('\n')), header_size + len(mock_data.BACKUP_SPEC['unmatched']) + footer_size)

    def test_should_render_a_message_when_backup_rules_are_available(self):
        result = render_rules_as_table(rules=[])
        self.assertEqual(result, 'No data')

    def test_should_render_a_message_when_no_matched_rules_are_available(self):
        result = render_matched_specification_as_table(state='included', spec=[])
        self.assertEqual(result, 'No data')

        result = render_matched_specification_as_table(state='excluded', spec=[])
        self.assertEqual(result, 'No data')

    def test_should_render_a_message_when_no_unmatched_rules_are_available(self):
        result = render_unmatched_specification_as_table(spec=[])
        self.assertEqual(result, 'No data')
