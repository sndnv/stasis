"""Default :class:`InitApi` implementation."""

import logging
from json import JSONDecodeError

import click
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

from client_cli.api.endpoint_context import EndpointContext
from client_cli.api.init_api import InitApi


class DefaultInitApi(InitApi):
    """Default, HTTP/JSON based :class:`InitApi` implementation."""

    def __init__(self, api_url: str, connect_retries: int, backoff_factor: float, context: EndpointContext):
        session = requests.Session()
        retry = Retry(connect=connect_retries, backoff_factor=backoff_factor)
        adapter = HTTPAdapter(max_retries=retry)
        session.mount(api_url, adapter)

        self.api_url = api_url
        self.context = context
        self.state_session = session

    def state(self):
        response = self.state_session.request(
            method='get',
            url='{}/init'.format(self.api_url),
            verify=self.context.verify
        )

        if response.ok:
            try:
                return response.json()
            except (JSONDecodeError, requests.exceptions.JSONDecodeError):
                logging.error(
                    'Response was [{}] but content is not JSON: [{}]'.format(
                        response.status_code,
                        response.content
                    )
                )
                raise click.Abort() from None
        else:
            logging.error('Request failed: [{} - {}]'.format(response.status_code, response.reason))
            if response.text:
                logging.error(response.text)

            raise click.Abort()

    def provide_credentials(self, username, password):
        response = requests.request(
            method='post',
            url='{}/init'.format(self.api_url),
            data={'username': username, 'password': password},
            verify=self.context.verify
        )

        if response.ok:
            return {'successful': True}
        else:
            logging.error('Request failed: [{} - {}]'.format(response.status_code, response.reason))
            if response.text:
                logging.error(response.text)

            raise click.Abort()
