#!/usr/bin/env python3

import argparse
import logging
import os
import shutil
import subprocess
import sys

DESCRIPTION = 'Generate new CSRs, x509 certificates and private keys.'


def main():
    parser = argparse.ArgumentParser(description=DESCRIPTION)

    parser.add_argument('common_name', type=str, help='Server name associated with certificate')
    parser.add_argument('-c', '--country', type=str, required=True, help='Certificate country')
    parser.add_argument('-l', '--location', type=str, required=True, help='Certificate location')
    parser.add_argument('-o', '--organization', type=str, default='stasis', help='Certificate organization')
    parser.add_argument('-v', '--validity', type=int, default=365, help='Certificate validity, in days')
    parser.add_argument('-k', '--key-size', type=int, default=4096, help='Private key size, in bits')
    parser.add_argument('-p', '--output-path', type=str, default='.', help='Path to use for generated files')
    parser.add_argument('-cc', '--ca-certificate-path', type=str, required=True, help='Path to root CA\'s certificate')
    parser.add_argument('-ck', '--ca-key-path', type=str, required=True, help='Path to root CA\'s private key')
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
    ca_cert_path = args.ca_certificate_path
    ca_key_path = args.ca_key_path

    private_key_size = args.key_size
    private_key_path = '{}/{}.key.pem'.format(output_path, common_name)

    x509_cert_validity = args.validity
    csr_path = '{}/{}.csr'.format(output_path, common_name)

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
                'CSR:\t{}'.format(csr_path),
                'PKCS12 File:\t{}'.format(pkcs12_path),
                'Extra Name:\t{}'.format(extra_name),
            ]
        )
    )

    csr_result = subprocess.run(
        [
            openssl_path,
            'req',
            '-new',
            '-nodes',  # no password
            '-subj', x509_subject,
            '-newkey', 'rsa:{}'.format(private_key_size),
            '-sha256',
            '-keyout', private_key_path,
            '-out', csr_path,
        ]
    ).returncode

    if csr_result == 0:
        x509_config_file_path = '{}/{}.tmp.cfg'.format(output_path, common_name)
        x509_cert_path = '{}/{}.cert.pem'.format(output_path, common_name)

        with open(x509_config_file_path, 'w') as configFile:
            if extra_name:
                san = 'subjectAltName=DNS.1:{},DNS.2:{}'.format(common_name, extra_name)
            else:
                san = 'subjectAltName=DNS:{}'.format(common_name)

            configFile.write(
                '\n'.join(
                    [
                        'authorityKeyIdentifier=keyid,issuer',
                        'basicConstraints=CA:FALSE',
                        'keyUsage=digitalSignature,nonRepudiation,keyEncipherment,dataEncipherment',
                        san,
                    ]
                )
            )

        x509_result = subprocess.run(
            [
                openssl_path,
                'x509',
                '-req',
                '-in', csr_path,
                '-CA', ca_cert_path,
                '-CAkey', ca_key_path,
                '-CAcreateserial',
                '-out', x509_cert_path,
                '-days', '{}'.format(x509_cert_validity),
                '-sha256',
                '-extfile', x509_config_file_path,
            ]
        ).returncode

        os.remove(csr_path)
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
