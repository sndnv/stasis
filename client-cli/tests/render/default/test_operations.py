import unittest
from uuid import uuid4

from client_cli.render.default.operations import render_as_table, render_operation_response
from tests.mocks import mock_data


class OperationsSpec(unittest.TestCase):

    def test_should_render_operations_as_a_table(self):
        table = render_as_table(operations=mock_data.ACTIVE_OPERATIONS)
        self.assertIn(mock_data.ACTIVE_OPERATIONS[0]['operation'], table)
        self.assertIn(mock_data.ACTIVE_OPERATIONS[1]['operation'], table)
        self.assertIn(mock_data.ACTIVE_OPERATIONS[2]['operation'], table)

    def test_should_render_a_message_when_no_operations_are_available(self):
        result = render_as_table(operations=[])
        self.assertEqual(result, 'No data')

    def test_should_render_operation_responses(self):
        operation = str(uuid4())
        self.assertEqual(
            render_operation_response(response={'successful': True, 'operation': operation}),
            'Started: {}'.format(operation)
        )

        self.assertEqual(
            render_operation_response(response={'successful': True}),
            'OK'
        )

        self.assertEqual(
            render_operation_response(response={'successful': False, 'failure': 'test failure'}),
            'Failed: test failure'
        )
