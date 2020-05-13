import json
from json import JSONDecodeError


class MockResponse:
    def __init__(self, status_code, response):
        self.status_code = status_code
        self.response = response
        self.text = str(response)
        self.closed = False

    @staticmethod
    def success(response=None):
        if response is None:
            response = {}

        return MockResponse(status_code=200, response=response)

    @staticmethod
    def failure(response=None):
        if response is None:
            response = {}

        return MockResponse(status_code=500, response=response)

    @staticmethod
    def empty():
        return MockResponse(status_code=200, response=None)

    @property
    def ok(self) -> bool:
        # pylint: disable=invalid-name
        return 200 >= self.status_code < 300

    @property
    def reason(self) -> str:
        return 'MockResponse / {}'.format('Success' if self.ok else 'Failure')

    @property
    def content(self) -> str:
        return json.dumps(self.response) if self.response else ''

    def json(self):
        if self.response is not None:
            return self.response
        else:
            raise JSONDecodeError("Empty response provided", '', 0)

    def __iter__(self):
        return iter(self.response)

    def close(self):
        self.closed = True
