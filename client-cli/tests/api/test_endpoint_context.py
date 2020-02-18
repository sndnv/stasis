import unittest
from unittest.mock import patch, mock_open

from click import Abort

from client_cli.api.endpoint_context import DefaultHttpsContext, CustomHttpsContext


class EndpointContextSpec(unittest.TestCase):

    def test_should_support_default_https_contexts(self):
        context = DefaultHttpsContext(verify=False)
        self.assertFalse(context.verify)

    @patch('cryptography.hazmat.primitives.serialization.pkcs12.load_key_and_certificates')
    def test_should_load_pkcs12_certificates(self, mock_load_cert):
        certificate_path = '/tmp/some/path'
        expected_certificate = MockCertificate(content=b'')
        mock_load_cert.return_value = (None, expected_certificate, None)

        with patch("builtins.open", mock_open(read_data=b'')) as mock_file_open:
            actual_certificate = CustomHttpsContext.load_pkcs12_certificate(
                certificate_path=certificate_path,
                certificate_password='test-password'
            )

            mock_file_open.assert_called_once_with(certificate_path, 'rb')

        mock_load_cert.assert_called_once()
        self.assertEqual(actual_certificate, expected_certificate)

    def test_should_read_pem_certificate_content(self):
        certificate_path = '/tmp/some/path'
        expected_content = b'a-b-c'
        with patch("builtins.open", mock_open(read_data=expected_content)) as mock_file_open:
            actual_content = CustomHttpsContext.read_pem_certificate_file(certificate_path=certificate_path)
            self.assertEqual(actual_content, expected_content)
            mock_file_open.assert_called_once_with(certificate_path, 'rb')

    def test_should_write_pem_certificate_content(self):
        # pylint: disable=no-self-use
        expected_content = b'a-b-c'
        with patch("builtins.open", mock_open(read_data=expected_content)) as mock_file_open:
            CustomHttpsContext.write_pem_certificate_file(
                certificate_path='/tmp/some/path',
                certificate_content=expected_content
            )

            mock_file_open().write.assert_called_once_with(expected_content)

    @patch('client_cli.api.endpoint_context.CustomHttpsContext.write_pem_certificate_file')
    @patch('client_cli.api.endpoint_context.CustomHttpsContext.read_pem_certificate_file')
    @patch('client_cli.api.endpoint_context.CustomHttpsContext.load_pkcs12_certificate')
    @patch('os.path.isfile')
    def test_should_create_missing_pem_files_for_custom_https_contexts(
            self,
            mock_isfile,
            mock_load_cert,
            mock_read_pem,
            mock_write_pem
    ):
        expected_content = b'a-b-c'
        mock_isfile.return_value = False
        mock_load_cert.return_value = MockCertificate(content=expected_content)

        pkcs12_path = '/tmp/some/path'
        pem_path = '{}.as.pem'.format(pkcs12_path)
        password = 'test-password'
        context = CustomHttpsContext(
            certificate_type='PKCS12',
            certificate_path=pkcs12_path,
            certificate_password=password
        )

        mock_isfile.assert_called_once_with(pem_path)
        mock_load_cert.assert_called_once_with(certificate_path=pkcs12_path, certificate_password=password)
        mock_read_pem.assert_not_called()
        mock_write_pem.assert_called_once_with(certificate_path=pem_path, certificate_content=expected_content)

        self.assertEqual(context.verify, pem_path)

    @patch('client_cli.api.endpoint_context.CustomHttpsContext.write_pem_certificate_file')
    @patch('client_cli.api.endpoint_context.CustomHttpsContext.read_pem_certificate_file')
    @patch('client_cli.api.endpoint_context.CustomHttpsContext.load_pkcs12_certificate')
    @patch('os.path.isfile')
    def test_should_recreate_invalid_pem_files_for_custom_https_contexts(
            self,
            mock_isfile,
            mock_load_cert,
            mock_read_pem,
            mock_write_pem
    ):
        expected_content = b'a-b-c'
        unexpected_content = b'd-e-f'
        mock_isfile.return_value = True
        mock_load_cert.return_value = MockCertificate(content=expected_content)
        mock_read_pem.return_value = unexpected_content

        pkcs12_path = '/tmp/some/path'
        pem_path = '{}.as.pem'.format(pkcs12_path)
        password = 'test-password'
        context = CustomHttpsContext(
            certificate_type='PKCS12',
            certificate_path=pkcs12_path,
            certificate_password=password
        )

        mock_isfile.assert_called_once_with(pem_path)
        mock_load_cert.assert_called_once_with(certificate_path=pkcs12_path, certificate_password=password)
        mock_read_pem.assert_called_once_with(certificate_path=pem_path)
        mock_write_pem.assert_called_once_with(certificate_path=pem_path, certificate_content=expected_content)

        self.assertEqual(context.verify, pem_path)

    @patch('client_cli.api.endpoint_context.CustomHttpsContext.write_pem_certificate_file')
    @patch('client_cli.api.endpoint_context.CustomHttpsContext.read_pem_certificate_file')
    @patch('client_cli.api.endpoint_context.CustomHttpsContext.load_pkcs12_certificate')
    @patch('os.path.isfile')
    def test_should_use_valid_pem_files_for_custom_https_contexts(
            self,
            mock_isfile,
            mock_load_cert,
            mock_read_pem,
            mock_write_pem
    ):
        expected_content = b'a-b-c'
        mock_isfile.return_value = True
        mock_load_cert.return_value = MockCertificate(content=expected_content)
        mock_read_pem.return_value = expected_content

        pkcs12_path = '/tmp/some/path'
        pem_path = '{}.as.pem'.format(pkcs12_path)
        password = 'test-password'
        context = CustomHttpsContext(
            certificate_type='PKCS12',
            certificate_path=pkcs12_path,
            certificate_password=password
        )

        mock_isfile.assert_called_once_with(pem_path)
        mock_load_cert.assert_called_once_with(certificate_path=pkcs12_path, certificate_password=password)
        mock_read_pem.assert_called_once_with(certificate_path=pem_path)
        mock_write_pem.assert_not_called()

        self.assertEqual(context.verify, pem_path)

    def test_should_fail_when_unsupported_certificate_used_for_custom_https_context(self):
        with self.assertRaises(Abort):
            CustomHttpsContext(
                certificate_type='JKS',
                certificate_path='/tmp/some/path',
                certificate_password='test-password'
            )


class MockCertificate:
    def __init__(self, content):
        self._content = content

    def public_bytes(self, encoding):
        # pylint: disable=unused-argument
        return self._content
