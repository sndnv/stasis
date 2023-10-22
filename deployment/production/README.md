# deployment / production

The provided `docker-compose.yml` defines all `stasis` services and their configuration.

## Getting Started

1) Generate/pull artifacts
2) Provide environment files (example files with all required parameters are available in [`secrets/examples`](./secrets/examples))
3) Provide TLS certificates and signature keys
4) Enable bootstrap for `identity` and `server` (first-run only; should be disabled normally)
5) Make sure that:
   * the `server` storage volume path (by default, `./local/server`) exists and is accessible/writable
   * the correct docker images are used for all services
   * the correct values for `PEKKO_HTTP_CORS_ALLOWED_ORIGINS` are set for both `identity` and `server`
6) Start services with `docker-compose up`

## After Bootstrap

1) **Replace management user** - for extra security, a new management user with a new password should be created and
the existing, default, management user should be removed. This can be done using the `server_create_user.sh` and
`server_delete_user.sh` scripts. This user needs to have the following permissions:
`view-self,view-privileged, view-public,view-service,manage-self,manage-privileged,manage-service` to be able to
manage the server.
2) **Create standard user** - for extra security, the management users and the users that own data/backups should be
separate. As with the management user, the `server_create_user.sh` script can be used; the difference between the
two types is only in the permissions that they are granted - `view-self,view-public,manage-self` for a standard user.
3) **Create devices** - after the standard user has been created, its devices can then be setup; this is done via the
`server_create_device.sh` script.
4) **Bootstrap devices** - for each new device, a bootstrap process needs to be performed and that is done on the
device itself. Using the `server_get_bootstrap_code.sh` script, a new bootstrap code can be generated for a specific device.
5) **Run a backup** - after the bootstrap process has been completed for a device, a dataset definition can then be
created and the first backup can be started. It might be a good idea to revise the backup rules and include/exclude
additional files or directories.
6) **Setup scheduling** - as a final setup step, backups can be configured to run periodically.

## Deployment Components

### [`bootstrap`](./bootstrap)

Contains bootstrap configuration files for `identity` and `server`.

### [`local`](./local)

Default location for the data persisted by the database and `server`; **files in this directory must be generated
locally and should not be part of any commits**.

### [`secrets`](./secrets)

Contains secrets used by `stasis` services; **files in this directory must be generated
locally and should not be part of any commits**.

### [`secrets/examples`](./secrets/examples)

Example environment files for configuring the services.

### [`scripts`](./scripts)

Contains basic scripts that help with the initial management of `server`.

#### `server_create_user.sh`

> Create new users via the server API

#### `server_delete_user.sh`

> Remove existing users via the server API

#### `server_create_device.sh`

> Create new devices via the server API

#### `server_get_bootstrap_code.sh`

> Retrieve bootstrap codes

#### `generate_user_password.py`

> Generate hashed user authentication passwords.

```
./generate_user_password.py --user-salt <user-salt-on-server>
```

Used by the server management scripts to generate the correct user authentication password.

#### `generate_cert.py`

> Generate new CSRs, x509 certificates and private keys.

```
./generate_cert.py -c <country> -l <location> -cc <CA certificate path> -ck <CA private key path>
```

Used to generate x509 certificates and private keys signed by the provided certificate authority.

#### `client_install.sh`

> Installs stasis-client and stasis-client-cli for the current user

#### `client_uninstall.sh`

> Uninstalls stasis-client and stasis-client-cli for the current user
