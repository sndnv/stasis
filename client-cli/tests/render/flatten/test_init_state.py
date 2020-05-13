import unittest

from client_cli.render.flatten.init_state import (
    flatten_primary_init_state,
    flatten_secondary_init_state,
    transform_init_state_cause
)
from tests.mocks import mock_data


class InitStateSpec(unittest.TestCase):

    def test_should_convert_pending_primary_init_state_to_successful_response(self):
        self.assertDictEqual(
            flatten_primary_init_state(init_state=mock_data.INIT_STATE_PENDING),
            {'successful': True}
        )

    def test_should_convert_successful_primary_init_state_to_failed_response(self):
        self.assertDictEqual(
            flatten_primary_init_state(init_state=mock_data.INIT_STATE_SUCCESSFUL),
            {'successful': False, 'failure': 'Initialization already completed'}
        )

    def test_should_convert_other_primary_init_states_to_failed_responses(self):
        self.assertDictEqual(
            flatten_primary_init_state(init_state=mock_data.INIT_STATE_FAILED),
            {'successful': False, 'failure': 'No or invalid credentials provided'}
        )

    def test_should_convert_successful_secondary_init_state_to_successful_response(self):
        self.assertDictEqual(
            flatten_secondary_init_state(init_state=mock_data.INIT_STATE_SUCCESSFUL),
            {'successful': True}
        )

    def test_should_convert_failed_secondary_init_state_to_failed_response(self):
        self.assertDictEqual(
            flatten_secondary_init_state(init_state=mock_data.INIT_STATE_FAILED),
            {'successful': False, 'failure': 'No or invalid credentials provided'}
        )

    def test_should_convert_other_secondary_init_states_to_failed_responses(self):
        self.assertDictEqual(
            flatten_secondary_init_state(init_state=mock_data.INIT_STATE_PENDING),
            {'successful': False, 'failure': 'Initialization did not complete; last state received was [pending]'}
        )

    def test_should_transform_init_state_failure_cause(self):
        failure_message = 'test failure'

        self.assertEqual(
            transform_init_state_cause(cause='credentials', message=failure_message),
            'No or invalid credentials provided'
        )

        self.assertEqual(
            transform_init_state_cause(cause='file', message=failure_message),
            failure_message
        )

        self.assertEqual(
            transform_init_state_cause(cause='token', message=failure_message),
            'JWT retrieval failed: [{}]'.format(failure_message)
        )

        self.assertEqual(
            transform_init_state_cause(cause='config', message=failure_message),
            'One or more invalid settings were encountered: [{}]'.format(failure_message)
        )

        self.assertEqual(
            transform_init_state_cause(cause='api', message=failure_message),
            'Client API failed to start: [{}]'.format(failure_message)
        )

        self.assertEqual(
            transform_init_state_cause(cause='unknown', message=failure_message),
            failure_message
        )

        self.assertEqual(
            transform_init_state_cause(cause='other', message=failure_message),
            failure_message
        )
