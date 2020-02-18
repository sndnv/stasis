"""Common functions used for API operations such as creating client APIs."""

import logging

from click import Abort

from client_cli.api.client_api import ClientApi
from client_cli.api.default_client_api import DefaultClientApi
from client_cli.api.endpoint_context import CustomHttpsContext, DefaultHttpsContext
from client_cli.api.inactive_client_api import InactiveClientApi


def create_api(config, api_token, insecure) -> ClientApi:
    """
    Creates a new client API with the provided configuration.

    If the default client API cannot be created (API token is missing) then an
    instance of :class:`InactiveClientApi` is returned instead.

    :param config: client configuration
    :param api_token: API token string or None if it is not available
    :param insecure: set to `True` to not verify TLS certificate when making requests to API
    :return: the client API or InactiveClientApi if it is not available
    """

    if api_token:
        api_type = config.get_string('stasis.client.api.type')
        if api_type.lower() != 'http':
            logging.error('Expected [http] API but [{}] found'.format(api_type))
            raise Abort()

        api_config = config.get_config('stasis.client.api.http')

        api_url = '{}://{}:{}'.format(
            'https' if api_config.get_bool('context.enabled') or insecure else 'http',
            api_config.get_string('interface'),
            api_config.get_int('port')
        )

        if api_config.get_bool('context.enabled') and not insecure:
            api_context = CustomHttpsContext(
                certificate_type=api_config.get_string('context.keystore.type'),
                certificate_path=api_config.get_string('context.keystore.path'),
                certificate_password=api_config.get_string('context.keystore.password')
            )
        else:
            api_context = DefaultHttpsContext(verify=not insecure)

        api = DefaultClientApi(
            api_url=api_url,
            api_token=api_token,
            context=api_context
        )
    else:
        api = InactiveClientApi()

    return api
