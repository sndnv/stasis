#!/usr/bin/env python3

import argparse
import logging
import os
import shutil
import subprocess
import sys

DESCRIPTION = 'Generate new self-signed x509 certificates and private keys.'


def main():
    parser = argparse.ArgumentParser(description=DESCRIPTION)

    parser.add_argument('common_name', type=str, help='Server name associated with certificate')
    parser.add_argument('-c', '--country', type=str, required=True, help='Certificate country')
    parser.add_argument('-l', '--location', type=str, required=True, help='Certificate location')
    parser.add_argument('-o', '--organization', type=str, default='stasis', help='Certificate organization')
    parser.add_argument('-v', '--validity', type=int, default=365, help='Certificate validity, in days')
    parser.add_argument('-k', '--key-size', type=int, default=4096, help='Private key size, in bits')
    parser.add_argument('-p', '--output-path', type=str, default='.', help='Path to use for generated files')
    parser.add_argument('-e', '--extra-name', type=str, default=None, help='Alternative server name')

    args = parser.parse_args()

    logging.basicConfig(
        format='[%(asctime)-15s] [%(levelname)s] [%(name)-5s]: %(message)s',
        level=logging.getLevelName(logging.INFO)
    )

    openssl_path = shutil.which("openssl")

    country = args.country
    location = args.location
    organization = args.organization
    common_name = args.common_name
    extra_name = args.extra_name
    output_path = args.output_path

    private_key_size = args.key_size
    private_key_path = '{}/{}.key.pem'.format(output_path, common_name)

    x509_cert_validity = args.validity
    x509_cert_path = '{}/{}.cert.pem'.format(output_path, common_name)

    pkcs12_path = '{}/{}.p12'.format(output_path, common_name)

    x509_subject = '/C={}/L={}/O={}/CN={}'.format(country, location, organization, common_name)

    logging.info(
        '\n\t'.join(
            [
                'Generating x509 certificate with:',
                'Subject:\t{}'.format(x509_subject),
                'Key Size:\t{} bits'.format(private_key_size),
                'Validity:\t{} day(s)'.format(x509_cert_validity),
                'Private Key:\t{}'.format(private_key_path),
                'Certificate:\t{}'.format(x509_cert_path),
                'PKCS12 File:\t{}'.format(pkcs12_path),
                'Extra Name:\t{}'.format(extra_name),
            ]
        )
    )

    x509_config_file_path = '{}/{}.tmp.cfg'.format(output_path, common_name)

    with open(x509_config_file_path, 'w') as configFile:
        configFile.write(
            '\n'.join(
                [
                    '[dn]',
                    'CN={}'.format(common_name),
                    '[req]',
                    'distinguished_name=dn',
                    '[EXT]',
                    'subjectAltName = @alt_names',
                    'keyUsage=digitalSignature',
                    'extendedKeyUsage=serverAuth',
                    'basicConstraints=CA:TRUE,pathlen:0',
                    '[alt_names]',
                    'DNS.1 = {}'.format(common_name),
                ] + (['DNS.2 = {}'.format(extra_name)] if extra_name else [])
            )
        )

    x509_result = subprocess.run(
        [
            openssl_path,
            'req',
            '-x509',
            '-nodes',  # no password
            '-subj', x509_subject,
            '-newkey', 'rsa:{}'.format(private_key_size),
            '-keyout', private_key_path,
            '-out', x509_cert_path,
            '-days', '{}'.format(x509_cert_validity),
            '-extensions', 'EXT',
            '-config', x509_config_file_path,
        ]
    ).returncode

    os.remove(x509_config_file_path)

    if x509_result == 0:
        pkcs12_result = subprocess.run(
            [
                openssl_path,
                'pkcs12',
                '-export',
                '-out', pkcs12_path,
                '-inkey', private_key_path,
                '-in', x509_cert_path,
                '-passout', 'pass:',  # no password
            ]
        ).returncode

        if pkcs12_result == 0:
            logging.info('Done')
        else:
            abort()
    else:
        abort()


def abort():
    sys.exit(1)


if __name__ == '__main__':
    main()
