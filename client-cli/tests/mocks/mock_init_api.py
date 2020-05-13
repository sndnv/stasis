from client_cli.api.init_api import InitApi
from tests.mocks import mock_data


class MockInitApi(InitApi):
    def __init__(self, state_responses=None):

        if state_responses is None:
            self.state_responses = [mock_data.INIT_STATE_SUCCESSFUL]
        else:
            self.state_responses = state_responses

        self.stats = {
            'state': 0,
            'provide_credentials': 0,
        }

    def state(self):
        state_retrieved_count = self.stats['state']
        self.stats['state'] += 1
        return self.state_responses[state_retrieved_count]

    def provide_credentials(self, username, password):
        self.stats['provide_credentials'] += 1
        return {'successful': True}
