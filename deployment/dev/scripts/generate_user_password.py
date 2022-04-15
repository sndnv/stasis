#!/usr/bin/env python3

import argparse
import logging
from getpass import getpass
import base64

from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC

DESCRIPTION = 'Generate hashed user authentication passwords.'

DEFAULT_ENCODING = 'utf-8'


def user_password_to_hashed_authentication_password(user_salt, user_password, salt_prefix, iterations, secret_size):
    """Derives an authentication password from the provided user password and config."""
    backend = default_backend()

    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA512(),
        length=secret_size,
        salt='{}-authentication-{}'.format(salt_prefix, user_salt).encode(DEFAULT_ENCODING),
        iterations=iterations,
        backend=backend
    )

    return kdf.derive(key_material=user_password.encode(DEFAULT_ENCODING))


def parse_args():
    parser = argparse.ArgumentParser(description=DESCRIPTION)

    parser.add_argument(
        '--authentication-password-size',
        type=int,
        default=16,
        help='Hashed authentication password size (in bytes)'
    )

    parser.add_argument(
        '--authentication-password-iterations',
        type=int,
        default=150000,
        help='Hashed authentication password iterations for derivation (PBKDF2)'
    )

    parser.add_argument(
        '--authentication-password-salt-prefix',
        type=str,
        default='changeme',
        help='Hashed authentication password salt prefix for derivation (PBKDF2)'
    )

    parser.add_argument('--user-salt', type=str, required=True, help='User salt')

    parser.add_argument('--user-password', type=str, default='', help='User password')

    parser.add_argument('-q', '--quiet', action='store_true', help='Print nothing but the output, if any')

    return parser.parse_args()


def main():
    args = parse_args()

    logging.basicConfig(
        format='[%(asctime)-15s] [%(levelname)s] [%(name)-5s]: %(message)s',
        level=logging.getLevelName(logging.ERROR if args.quiet else logging.INFO)
    )

    user_salt = args.user_salt

    if not args.user_password:
        user_password = getpass(prompt='User Password: ')
    else:
        user_password = args.user_password

    secret_size = args.authentication_password_size
    iterations = args.authentication_password_iterations
    salt_prefix = args.authentication_password_salt_prefix

    hashed_authentication_password = user_password_to_hashed_authentication_password(
        user_salt=user_salt,
        user_password=user_password,
        salt_prefix=salt_prefix,
        iterations=iterations,
        secret_size=secret_size
    )

    result = base64.urlsafe_b64encode(hashed_authentication_password).rstrip(b'=').decode(DEFAULT_ENCODING)
    print(result) if args.quiet else logging.info('Hashed Password: [{}]'.format(result))


if __name__ == '__main__':
    main()
