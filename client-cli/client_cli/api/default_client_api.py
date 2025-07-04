"""Default :class:`ClientApi` implementation."""

import logging
from json import JSONDecodeError, loads

import click
import requests
from sseclient import SSEClient

from client_cli.api.client_api import ClientApi
from client_cli.api.endpoint_context import EndpointContext


class DefaultClientApi(ClientApi):
    """Default, HTTP/JSON based :class:`ClientApi` implementation."""

    def __init__(self, api_url: str, api_token: str, context: EndpointContext, timeout: int):
        self.api_url = api_url
        self.api_token = api_token
        self.context = context
        self.timeout = timeout

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

    def dataset_definition(self, definition):
        return self.get(url='/datasets/definitions/{}'.format(definition))

    def dataset_definition_delete(self, definition):
        return self.delete(url='/datasets/definitions/{}'.format(definition))

    def dataset_definitions(self):
        return self.get(url='/datasets/definitions')

    def dataset_entries(self):
        return self.get(url='/datasets/entries')

    def dataset_entries_for_definition(self, definition):
        return self.get(url='/datasets/entries/for-definition/{}'.format(definition))

    def dataset_entry_delete(self, entry):
        return self.delete(url='/datasets/entries/{}'.format(entry))

    def user(self):
        return self.get(url='/user')

    def user_password_update(self, request):
        return self.put(url='/user/password', data=request)

    def user_salt_update(self, request):
        return self.put(url='/user/salt', data=request)

    def device(self):
        return self.get(url='/device')

    def device_connections(self):
        return self.get(url='/device/connections')

    def device_reencrypt_secret(self, request):
        return self.put(url='/device/key/re-encrypt', data=request)

    def device_commands(self):
        return self.get(url='/device/commands')

    def operations(self, state):
        return self.get(url='/operations', params={'state': state})

    def operation_progress(self, operation):
        return self.get(url='/operations/{}/progress'.format(operation))

    def operation_follow(self, operation):
        response = self.get_stream(url='/operations/{}/follow'.format(operation))
        client = SSEClient(event_source=response)

        try:
            for event in client.events():
                if event.data:
                    progress = loads(event.data)
                    yield progress
                    if 'completed' in progress and progress['completed']:
                        return
        finally:
            client.close()

    def operation_stop(self, operation):
        return self.put(url='/operations/{}/stop'.format(operation))

    def operation_resume(self, operation):
        return self.put(url='/operations/{}/resume'.format(operation))

    def operation_remove(self, operation):
        return self.delete(url='/operations/{}'.format(operation))

    def backup_rules(self):
        return self.get(url='/operations/backup/rules')

    def backup_rules_for_definition(self, definition):
        return self.get(url='/operations/backup/rules/{}'.format(definition or 'default'))

    def backup_specification_for_definition(self, definition):
        return self.get(url='/operations/backup/rules/{}/specification'.format(definition or 'default'))

    def backup_start(self, definition):
        return self.put(url='/operations/backup/{}'.format(definition))

    def backup_define(self, request):
        return self.post(url='/datasets/definitions', data=request)

    def backup_update(self, definition, request):
        return self.put(url='/datasets/definitions/{}'.format(definition), data=request)

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

    def analytics_state(self):
        return self.get(url='/service/analytics')

    def analytics_state_send(self):
        return self.put(url='/service/analytics/send')

    def get(self, url, params=None):
        """
        Executes a `GET` request for the specified URL with the provided query parameters.

        :param url: URL to use for request (ex: /schedules)
        :param params: query parameters (if any)
        :return: endpoint response
        """
        return self.request(method='get', url=url, params=params)

    def get_stream(self, url, params=None):
        """
        Executes a `GET` request for the specified URL with the provided query parameters, with streaming enabled.

        :param url: URL to use for request (ex: /schedules)
        :param params: query parameters (if any)
        :return: endpoint response stream
        """
        return self.request_stream(method='get', url=url, params=params)

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

    def delete(self, url, params=None, data=None):
        """
        Executes a `DELETE` request for the specified URL with the provided query parameters and request data.

        :param url: URL to use for request (ex: /schedules)
        :param params: query parameters (if any)
        :param data: request data (if any)
        :return: endpoint response
        """
        return self.request(method='delete', url=url, params=params, data=data)

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
            verify=self.context.verify,
            timeout=self.timeout
        )

        if response.ok:
            try:
                result = response.json()
            except (JSONDecodeError, requests.exceptions.JSONDecodeError):
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

    def request_stream(self, method, url, params=None, data=None):
        """
        Executes a request with the specified method for the specified URL with
        the provided query parameters and request data, with streaming enabled.

        :param method: HTTP method to use for request
        :param url: URL to use for request (ex: /schedules)
        :param params: query parameters (if any)
        :param data: request data (if any)
        :return: endpoint response stream
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
            verify=self.context.verify,
            stream=True,
            timeout=self.timeout
        )

        if response.ok:
            return response
        else:
            logging.error('Request failed: [{} - {}]'.format(response.status_code, response.reason))
            raise click.Abort()
