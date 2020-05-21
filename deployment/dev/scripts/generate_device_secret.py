#!/usr/bin/env python3

import argparse
import logging
import os
import random
import string
from getpass import getpass
from uuid import UUID

from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC

DESCRIPTION = 'Generate encrypted device secrets.'

DEFAULT_ENCODING = 'utf-8'


def user_password_to_hashed_encryption_password(user_salt, user_password, salt_prefix, iterations, secret_size):
    """Derives an encryption password from the provided user password and config."""
    backend = default_backend()

    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA512(),
        length=secret_size,
        salt='{}-encryption-{}'.format(salt_prefix, user_salt).encode(DEFAULT_ENCODING),
        iterations=iterations,
        backend=backend
    )

    return kdf.derive(key_material=user_password.encode(DEFAULT_ENCODING))


def hashed_encryption_password_to_encryption_secret(user_id, hashed_encryption_password, key_size, iv_size):
    """Derives encryption secret cryptomaterial (key, IV) from the provided encryption password and config."""
    backend = default_backend()

    salt = UUID(user_id).bytes

    key_info = '{}-encryption-key'.format(user_id).encode(DEFAULT_ENCODING)
    iv_info = '{}-encryption-iv'.format(user_id).encode(DEFAULT_ENCODING)

    key_kdf = HKDF(
        algorithm=hashes.SHA512(),
        length=key_size,
        salt=salt,
        info=key_info,
        backend=backend
    )

    iv_kdf = HKDF(
        algorithm=hashes.SHA512(),
        length=iv_size,
        salt=salt,
        info=iv_info,
        backend=backend
    )

    key = key_kdf.derive(key_material=hashed_encryption_password)
    iv = iv_kdf.derive(key_material=hashed_encryption_password)

    return key, iv


def encrypt_device_secret(encryption_secret_key, encryption_secret_iv, device_secret):
    """Encrypts (AES/GCM) the provided device secret using the specified key and IV."""
    aes = AESGCM(encryption_secret_key)

    return aes.encrypt(
        nonce=encryption_secret_iv,
        data=device_secret.encode(DEFAULT_ENCODING),
        associated_data=None
    )


def write_encrypted_device_secret(encrypted_device_secret, target_file):
    """Writes the provided encrypted device secret to the specified file."""
    with os.fdopen(os.open(target_file, flags=os.O_CREAT | os.O_WRONLY, mode=0o644), 'wb') as file:
        file.write(encrypted_device_secret)


def parse_args():
    parser = argparse.ArgumentParser(description=DESCRIPTION)

    parser.add_argument(
        '--encryption-password-size',
        type=int,
        default=32,
        help='Hashed encryption password size (in bytes)'
    )

    parser.add_argument(
        '--encryption-password-iterations',
        type=int,
        default=150000,
        help='Hashed encryption password iterations for derivation (PBKDF2)'
    )

    parser.add_argument(
        '--encryption-password-salt-prefix',
        type=str,
        default='changeme',
        help='Hashed encryption password salt prefix for derivation (PBKDF2)'
    )

    parser.add_argument(
        '--encryption-secret-key-size',
        type=int,
        default=16,
        help='Encryption secret key size (in bytes)'
    )

    parser.add_argument(
        '--encryption-secret-iv-size',
        type=int,
        default=12,
        help='Encryption secret IV size (in bytes)'
    )

    parser.add_argument('--user-id', type=str, required=True, help='User UUID')

    parser.add_argument('--user-salt', type=str, required=True, help='User salt')

    parser.add_argument('--user-password', type=str, default='', help='User password')

    parser.add_argument('--device-secret', type=str, default='', help='Device secret')

    parser.add_argument(
        '--output-path',
        type=str,
        default='client.secret',
        help='Path to generated device secret'
    )

    return parser.parse_args()


def main():
    logging.basicConfig(
        format='[%(asctime)-15s] [%(levelname)s] [%(name)-5s]: %(message)s',
        level=logging.getLevelName(logging.INFO)
    )

    args = parse_args()

    user_id = args.user_id
    user_salt = args.user_salt

    if not args.user_password:
        user_password = getpass(prompt='User Password: ')
    else:
        user_password = args.user_password

    if not args.device_secret:
        device_secret = ''.join(random.choices(string.ascii_letters + string.digits, k=16))
    else:
        device_secret = args.device_secret

    secret_size = args.encryption_password_size
    iterations = args.encryption_password_iterations
    salt_prefix = args.encryption_password_salt_prefix

    key_size = args.encryption_secret_key_size
    iv_size = args.encryption_secret_iv_size

    encrypted_device_secret_file = args.output_path

    hashed_encryption_password = user_password_to_hashed_encryption_password(
        user_salt=user_salt,
        user_password=user_password,
        salt_prefix=salt_prefix,
        iterations=iterations,
        secret_size=secret_size
    )

    encryption_secret_key, encryption_secret_iv = hashed_encryption_password_to_encryption_secret(
        user_id=user_id,
        hashed_encryption_password=hashed_encryption_password,
        key_size=key_size,
        iv_size=iv_size
    )

    encrypted_device_secret = encrypt_device_secret(
        encryption_secret_key=encryption_secret_key,
        encryption_secret_iv=encryption_secret_iv,
        device_secret=device_secret
    )

    write_encrypted_device_secret(
        encrypted_device_secret=encrypted_device_secret,
        target_file=encrypted_device_secret_file
    )


if __name__ == '__main__':
    main()
