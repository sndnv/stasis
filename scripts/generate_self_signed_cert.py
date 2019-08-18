#!/usr/bin/env python3

import shutil
import subprocess
import sys
import argparse

EXIT_OK = 0
EXIT_X509_FAILED = 1
EXIT_PKCS12_FAILED = 2

parser = argparse.ArgumentParser(description='Generate new self-signed x509 certificates and private keys.')
parser.add_argument('common_name', type=str, help='Server name associated with certificate')
parser.add_argument('-c', '--country', type=str, required=True, help='Certificate country')
parser.add_argument('-l', '--location', type=str, required=True, help='Certificate location')
parser.add_argument('-o', '--organization', type=str, default='stasis', help='Certificate country')
parser.add_argument('-v', '--validity', type=int, default=365, help='Certificate validity, in days')
parser.add_argument('-k', '--key-size', type=int, default=4096, help='Private key size, in bits')
parser.add_argument('-p', '--output-path', type=str, default='.', help='Path to use for generated files')

args = parser.parse_args()

openssl_path = shutil.which("openssl")

country = args.country
location = args.location
organization = args.organization
common_name = args.common_name
output_path = args.output_path

private_key_size = args.key_size
private_key_path = '{}/{}.key.pem'.format(output_path, common_name)

x509_cert_validity = args.validity
x509_cert_path = '{}/{}.cert.pem'.format(output_path, common_name)

pkcs12_path = '{}/{}.p12'.format(output_path, common_name)

x509_subject = '/C={}/L={}/O={}/CN={}'.format(country, location, organization, common_name)

print(
    '\n\t'.join(
        [
            'Generating x509 certificate with:',
            'Subject:\t{}'.format(x509_subject),
            'Key Size:\t{} bits'.format(private_key_size),
            'Validity:\t{} day(s)'.format(x509_cert_validity),
            'Private Key:\t{}'.format(private_key_path),
            'Certificate:\t{}'.format(x509_cert_path),
            'PKCS12 File:\t{}'.format(pkcs12_path)
        ]
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
        '-days', '{}'.format(x509_cert_validity)
    ]
).returncode

if x509_result == 0:
    pkcs12_result = subprocess.run(
        [
            openssl_path,
            'pkcs12',
            '-export',
            '-out', pkcs12_path,
            '-inkey', private_key_path,
            '-in', x509_cert_path,
            '-passout', 'pass:'  # no password
        ]
    ).returncode

    if x509_result == 0:
        print('Done')
        sys.exit(EXIT_OK)
    else:
        sys.exit(EXIT_PKCS12_FAILED)
else:
    sys.exit(EXIT_X509_FAILED)
