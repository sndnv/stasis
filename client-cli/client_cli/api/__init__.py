"""Common functions used for API operations such as creating client APIs."""

import logging

from click import Abort

from client_cli.api.client_api import ClientApi
from client_cli.api.default_client_api import DefaultClientApi
from client_cli.api.default_init_api import DefaultInitApi
from client_cli.api.endpoint_context import CustomHttpsContext, DefaultHttpsContext, InvalidCertificateFailure
from client_cli.api.inactive_client_api import InactiveClientApi
from client_cli.api.inactive_init_api import InactiveInitApi
from client_cli.api.init_api import InitApi


def create_client_api(config, timeout, api_token, insecure, on_custom_cert_expired) -> ClientApi:
    """
    Creates a new client API with the provided configuration.

    If the default client API cannot be created (API token is missing) then an
    instance of :class:`InactiveClientApi` is returned instead.

    If an invalid certificate is encountered, `on_custom_cert_expired` is called and certificate
    loading is retried; the expectation is that the handler will attempt to rotate the certificate.

    :param config: client configuration
    :param timeout: API request timeout
    :param api_token: API token string or None if it is not available
    :param insecure: set to `True` to not verify TLS certificate when making requests to API
    :param on_custom_cert_expired: called when a certificate is found to be expired/invalid
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
            try:
                api_context = CustomHttpsContext(
                    certificate_type=api_config.get_string('context.keystore.type'),
                    certificate_path=api_config.get_string('context.keystore.path'),
                    certificate_password=api_config.get_string('context.keystore.password')
                )
            except InvalidCertificateFailure:
                on_custom_cert_expired()
                api_context = CustomHttpsContext(
                    certificate_type=api_config.get_string('context.keystore.type'),
                    certificate_path=api_config.get_string('context.keystore.path'),
                    certificate_password=api_config.get_string('context.keystore.password')
                )
        else:
            api_context = DefaultHttpsContext(verify=not insecure)

        default_client = DefaultClientApi(
            api_url=api_url,
            api_token=api_token,
            context=api_context,
            timeout=timeout
        )

        if default_client.is_active():
            api = default_client
        else:
            api = InactiveClientApi()
    else:
        api = InactiveClientApi()

    return api


def create_init_api(config, timeout, insecure, client_api, on_custom_cert_expired) -> InitApi:
    """
    Creates a new initialization API with the provided configuration.

    If the client API is reported as active (i.e. initialization is already done) then an
    instance of :class:`InactiveInitApi` is returned instead.

    If an invalid certificate is encountered, `on_custom_cert_expired` is called and certificate
    loading is retried; the expectation is that the handler will attempt to rotate the certificate.

    :param config: client configuration
    :param timeout: API request timeout
    :param insecure: set to `True` to not verify TLS certificate when making requests to API
    :param client_api: client API to use for determining API state
    :param on_custom_cert_expired: called when a certificate is found to be expired/invalid
    :return: the init API or InactiveInitApi if it is not available
    """
    if not client_api.is_active():
        api_config = config.get_config('stasis.client.api.init')

        api_url = '{}://{}:{}'.format(
            'https' if api_config.get_bool('context.enabled') or insecure else 'http',
            api_config.get_string('interface'),
            api_config.get_int('port')
        )

        if api_config.get_bool('context.enabled') and not insecure:
            try:
                api_context = CustomHttpsContext(
                    certificate_type=api_config.get_string('context.keystore.type'),
                    certificate_path=api_config.get_string('context.keystore.path'),
                    certificate_password=api_config.get_string('context.keystore.password')
                )
            except InvalidCertificateFailure:
                on_custom_cert_expired()
                api_context = CustomHttpsContext(
                    certificate_type=api_config.get_string('context.keystore.type'),
                    certificate_path=api_config.get_string('context.keystore.path'),
                    certificate_password=api_config.get_string('context.keystore.password')
                )
        else:
            api_context = DefaultHttpsContext(verify=not insecure)

        api = DefaultInitApi(
            api_url=api_url,
            context=api_context,
            connect_retries=10,
            backoff_factor=0.1,
            timeout=timeout
        )
    else:
        api = InactiveInitApi()

    return api
