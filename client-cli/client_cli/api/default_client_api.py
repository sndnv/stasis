"""Default :class:`ClientApi` implementation."""

import logging
from json import JSONDecodeError

import click
import requests

from client_cli.api.client_api import ClientApi
from client_cli.api.endpoint_context import EndpointContext


class DefaultClientApi(ClientApi):
    """Default, HTTP/JSON based :class:`ClientApi` implementation."""

    def __init__(self, api_url: str, api_token: str, context: EndpointContext):
        self.api_url = api_url
        self.api_token = api_token
        self.context = context

    def is_active(self):
        try:
            return bool(self.get(url='/service/ping').get('id', None))
        except (click.Abort, requests.exceptions.ConnectionError):
            return False

    def stop(self):
        try:
            return self.put(url='/service/stop')
        except click.Abort:
            return {'successful': False}

    def dataset_metadata(self, entry):
        return self.get(url='/datasets/metadata/{}'.format(entry))

    def dataset_metadata_search(self, search_query, until):
        return self.get(url='/datasets/metadata/search', params={'query': search_query, 'until': until})

    def dataset_definitions(self):
        return self.get(url='/datasets/definitions')

    def dataset_entries(self):
        return self.get(url='/datasets/entries')

    def dataset_entries_for_definition(self, definition):
        return self.get(url='/datasets/entries/{}'.format(definition))

    def user(self):
        return self.get(url='/user')

    def device(self):
        return self.get(url='/device')

    def device_connections(self):
        return self.get(url='/device/connections')

    def operations(self):
        return self.get(url='/operations')

    def operation_stop(self, operation):
        return self.put(url='/operations/{}/stop'.format(operation))

    def backup_start(self, definition):
        return self.put(url='/operations/backup/{}'.format(definition))

    def backup_define(self, request):
        return self.post(url='/datasets/definitions', data=request)

    def recover_until(self, definition, until, path_query, destination, discard_paths):
        params = {'query': path_query, 'destination': destination, 'keep_structure': not discard_paths}
        return self.put(url='/operations/recover/{}/until/{}'.format(definition, until), params=params)

    def recover_from(self, definition, entry, path_query, destination, discard_paths):
        params = {'query': path_query, 'destination': destination, 'keep_structure': not discard_paths}
        return self.put(url='/operations/recover/{}/from/{}'.format(definition, entry), params=params)

    def recover_from_latest(self, definition, path_query, destination, discard_paths):
        params = {'query': path_query, 'destination': destination, 'keep_structure': not discard_paths}
        return self.put(url='/operations/recover/{}/latest'.format(definition), params=params)

    def schedules_public(self):
        return self.get(url='/schedules/public')

    def schedules_configured(self):
        return self.get(url='/schedules/configured')

    def schedules_configured_refresh(self):
        return self.put(url='/schedules/configured/refresh')

    def get(self, url, params=None):
        """
        Executes a `GET` request for the specified URL with the provided query parameters.

        :param url: URL to use for request (ex: /schedules)
        :param params: query parameters (if any)
        :return: endpoint response
        """
        return self.request(method='get', url=url, params=params)

    def put(self, url, params=None, data=None):
        """
        Executes a `PUT` request for the specified URL with the provided query parameters and request data.

        :param url: URL to use for request (ex: /schedules)
        :param params: query parameters (if any)
        :param data: request data (if any)
        :return: endpoint response
        """
        return self.request(method='put', url=url, params=params, data=data)

    def post(self, url, params=None, data=None):
        """
        Executes a `POST` request for the specified URL with the provided query parameters and request data.

        :param url: URL to use for request (ex: /schedules)
        :param params: query parameters (if any)
        :param data: request data (if any)
        :return: endpoint response
        """
        return self.request(method='post', url=url, params=params, data=data)

    def request(self, method, url, params=None, data=None):
        """
        Executes a request with the specified method for the specified URL with
        the provided query parameters and request data.

        :param method: HTTP method to use for request
        :param url: URL to use for request (ex: /schedules)
        :param params: query parameters (if any)
        :param data: request data (if any)
        :return: endpoint response
        """
        if params is None:
            params = {}

        if data is None:
            data = {}

        response = requests.request(
            method=method,
            url='{}{}'.format(self.api_url, url),
            params=params,
            headers={'Authorization': 'Bearer {}'.format(self.api_token)},
            json=data,
            verify=self.context.verify
        )

        if response.ok:
            try:
                result = response.json()
            except JSONDecodeError:
                logging.debug(
                    'Response was [{}] but content is not JSON: [{}]'.format(
                        response.status_code,
                        response.content
                    )
                )
                result = {}

            if method == 'get':
                return result
            else:
                return {'successful': True, 'operation': result.get('operation', None)}
        else:
            logging.error('Request failed: [{} - {}]'.format(response.status_code, response.reason))
            if response.text:
                logging.error(response.text)

            raise click.Abort()
