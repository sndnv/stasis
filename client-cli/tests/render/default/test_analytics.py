import unittest

from client_cli.render.default.analytics import render
from tests.mocks import mock_data


class AnalyticsSpec(unittest.TestCase):

    def test_should_render_analytics_state(self):
        result = render(state=mock_data.ANALYTICS)

        self.assertIn('runtime:', result)
        self.assertIn('id:', result)
        self.assertIn('app:', result)
        self.assertIn('jre:', result)
        self.assertIn('os:', result)
        self.assertIn('events (2):', result)
        self.assertIn('0 - test-event-1', result)
        self.assertIn('1 - test-event-2', result)
        self.assertIn('failures (1):', result)
        self.assertIn('Test failure', result)
        self.assertIn('created:', result)
        self.assertIn('updated:', result)
        self.assertIn('cached:', result)
        self.assertIn('transmitted:', result)
