"""Configuration for verifying backend (API) TLS connections."""
import datetime
import logging
import os
from abc import ABC, abstractmethod

from click import Abort
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives.serialization import Encoding
from cryptography.hazmat.primitives.serialization import pkcs12


class EndpointContext(ABC):
    """Configuration for verifying backend (API) TLS connections."""

    @property
    @abstractmethod
    def verify(self):
        """
        Denotes whether the client API's TLS certificate should be verified or not.

        :return: True, if the TLS certificate should be verified or a path to a certificate to be used for verification
        """


class DefaultHttpsContext(EndpointContext):
    """Default system connection context."""

    def __init__(self, verify: bool):
        self._verify = verify

    @property
    def verify(self):
        return self._verify


class CustomHttpsContext(EndpointContext):
    """Custom connection context for use with PKCS#12 files."""

    def __init__(self, certificate_type, certificate_path, certificate_password):
        if certificate_type.lower() != 'pkcs12':
            logging.error('Expected [PKCS12] certificate but [{}] provided'.format(certificate_type))
            raise Abort()

        self._pem_certificate_path = '{}.as.pem'.format(certificate_path)

        CustomHttpsContext._create_context_pem_file(
            pkcs12_certificate_path=certificate_path,
            pkcs12_certificate_password=certificate_password,
            pem_certificate_path=self._pem_certificate_path
        )

    @property
    def verify(self):
        return self._pem_certificate_path

    @staticmethod
    def _create_context_pem_file(pkcs12_certificate_path, pkcs12_certificate_password, pem_certificate_path):
        pkcs12_certificate = CustomHttpsContext.load_pkcs12_certificate(
            certificate_path=pkcs12_certificate_path,
            certificate_password=pkcs12_certificate_password
        )

        CustomHttpsContext.validate_pkcs12_certificate(
            pkcs12_certificate_path=pkcs12_certificate_path,
            pkcs12_certificate=pkcs12_certificate
        )

        pem_certificate_content = pkcs12_certificate.public_bytes(encoding=Encoding.PEM)

        if os.path.isfile(pem_certificate_path):
            existing_pem_certificate_content = CustomHttpsContext.read_pem_certificate_file(
                certificate_path=pem_certificate_path
            )

            if existing_pem_certificate_content != pem_certificate_content:
                logging.warning(
                    'Content in PEM file [{}] not same as PKCS12 certificate [{}]; recreating PEM file...'.format(
                        pem_certificate_path,
                        pkcs12_certificate_path
                    )
                )

                CustomHttpsContext.write_pem_certificate_file(
                    certificate_path=pem_certificate_path,
                    certificate_content=pem_certificate_content
                )

                logging.info(
                    'Successfully recreated PEM file [{}] from PKCS12 certificate [{}]'.format(
                        pem_certificate_path,
                        pkcs12_certificate_path
                    )
                )
            else:
                logging.debug(
                    'Content in PEM file [{}] same as PKCS12 certificate [{}]; skipping PEM file recreation'.format(
                        pem_certificate_path,
                        pkcs12_certificate_path
                    )
                )
        else:
            CustomHttpsContext.write_pem_certificate_file(
                certificate_path=pem_certificate_path,
                certificate_content=pem_certificate_content
            )

            logging.debug(
                'Successfully created PEM file [{}] from PKCS12 certificate [{}]'.format(
                    pem_certificate_path,
                    pkcs12_certificate_path
                )
            )

    @staticmethod
    def load_pkcs12_certificate(certificate_path, certificate_password):
        """
        Loads a PKCS#12 as a certificate object.

        :param certificate_path: path to certificate file
        :param certificate_password: certificate password
        :return: certificate object
        """

        with open(certificate_path, 'rb') as pkcs12_certificate_file:
            (_, pkcs12_certificate, _) = pkcs12.load_key_and_certificates(
                data=pkcs12_certificate_file.read(),
                password=certificate_password.encode('utf-8'),
                backend=default_backend()
            )

        return pkcs12_certificate

    @staticmethod
    def validate_pkcs12_certificate(pkcs12_certificate_path, pkcs12_certificate):
        """
        Checks if the provided certificate is currently valid and raises an
        exception if either it is not valid yet or has expired.

        :param pkcs12_certificate_path: path to certificate file
        :param pkcs12_certificate: actual loaded certificate
        :raises InvalidCertificateFailure if an invalid certificate is provided
        """
        now = datetime.datetime.now(datetime.UTC)

        if pkcs12_certificate.not_valid_before_utc > now:
            raise InvalidCertificateFailure(
                'API certificate [{}] not valid before [{}]'.format(
                    pkcs12_certificate_path,
                    pkcs12_certificate.not_valid_before_utc
                )
            )

        if now > pkcs12_certificate.not_valid_after_utc:
            raise InvalidCertificateFailure(
                'API certificate [{}] not valid after [{}]'.format(
                    pkcs12_certificate_path,
                    pkcs12_certificate.not_valid_after_utc
                )
            )

    @staticmethod
    def read_pem_certificate_file(certificate_path):
        """
        Reads the content of the specified PEM certificate file.

        :param certificate_path: path to certificate file
        :return: file content
        """

        with open(certificate_path, 'rb') as existing_pem_certificate_file:
            existing_pem_certificate_content = existing_pem_certificate_file.read()

        return existing_pem_certificate_content

    @staticmethod
    def write_pem_certificate_file(certificate_path, certificate_content):
        """
        Writes the supplied content to the specified PEM certificate file.

        :param certificate_path: path to certificate file
        :param certificate_content: file content
        """

        with open(certificate_path, 'wb') as pem_certificate_file:
            pem_certificate_file.write(certificate_content)


class InvalidCertificateFailure(Exception):
    """
    Raised when certificates validation issues are encountered.
    """
