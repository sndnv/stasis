# deployment / production

The provided `docker-compose.yml` defines all `stasis` services and their configuration.

## Getting Started

1) Prepare deployment configuration
    * Run script in [`./scripts/server_prepare_deployment.sh`](./scripts/server_prepare_deployment.sh)
    * Or, provide environment files (template files with all required parameters are available in [
      `secrets/templates`](./secrets/templates))
2) Set correct artifact versions in compose file
    * see below for more details on how some of the env vars have to be setup and how they relate to each other
3) Provide TLS certificates and signature keys
    * Make sure to follow your certificate authority's guidelines on generating TLS certificates
    * Alternatively, a root CA private key and certificate can be used with the `generate_cert.py` script to create new TLS
      certificates
4) Enable bootstrap for `identity` and `server`
    * This can be done by setting `STASIS_IDENTITY_BOOTSTRAP_MODE` and `STASIS_SERVER_SERVICE_BOOTSTRAP_MODE` to `init`
    * After the bootstrap process has completed, both services will pause, waiting for the mode to be set back to `off` (and
      restarted)
    * Normally, the bootstrap process should be able to proceed regardless of failures but not all issues can be ignored. If there
      were configuration problems that resulted in the bootstrap mode partially completing, the mode can be set to `drop`, which
      will cause the service to clean up all database-related information. It can then be set back to `init` to restart the
      process from scratch again.
5) Make sure that:
    * the `server` storage volume path (by default, `./local/server`) exists and is accessible/writable
    * the correct (latest) images are used for all services
    * the correct values for `PEKKO_HTTP_CORS_ALLOWED_ORIGINS` are set for both `identity` and `server`
    * the correct values for `NGINX_CORS_ALLOWED_ORIGIN` are set for both `identity-ui` and `server-ui`
6) Start services with `docker-compose up` (or `podman-compose up`)
    * Starting all services at the same time should eventually succeed, but it is easier to start bootstrapping them one by one,
      in the following order:
        1) `identity` and its database - other components depend on the OAuth service so it should be the first one to go
        2) `identity-ui` - after `identity` itself is up-and-running, getting its web UI working will allow easier debugging of
           authentication issues further on
        3) `server` and its database
        4) `server-ui`
        5) `db-*-exporter`, `prometheus` and `grafana` - the metrics collection can be the last one to go; it is all
           pre-configured so it should _just work_ after all other services are successfully running

### Generating a self-signed root CA

If a proper root CA is not available, a self-signed one can be generated using (some variation of) the following commands:

```
# generates a new private key for the CA
openssl genrsa -aes256 -out rootCA.key 4096

# generates a new certificate for the CA, using the previously generated private key
openssl req -x509 -new -nodes -key rootCA.key -sha256 -days 1024 -out rootCA.crt
```

### Generating signature keys

The `identity` service requires a signature key (Json Web Key / JWK) to be able to generate valid JWTs. Currently, it can be
configured to generate those keys on its own (either on every run or to generate a key once and save it). If, however, a custom
key needs to be provided, examples of the expected format are available [here](../../identity/src/test/resources/keys).

> Make sure to create cryptographically strong keys! The provided examples are not meant to be used in production and are
> generated with testing performance in mind.

### Configuring Cross-Origin Resource Sharing (CORS)

The default CORS configuration _should_ work; however, if the DNS names and/or ports are updated, then the CORS environment
variables should be updated as well.

#### Common CORS issues

In order to diagnose these problems, it is best to set `STASIS_IDENTITY_LOGLEVEL` and `STASIS_SERVER_LOGLEVEL` to `DEBUG` so that
the requests and responses can be logged.

Additionally, the browser's console and network inspection facilities can be used to determine what `Origin` headers are sent for
the requests and what `Access-Control-Allow-Origin` (if any) are sent in the responses.

##### `null` origin in request

Under some circumstances, the browser sends a request with a `null` origin and there is not much that can be done about it, other
than setting the allowed origins to `*`. Usually, that impacts the web UIs so their `NGINX_CORS_ALLOWED_ORIGIN` env vars are set
to `*` by default.

##### CORS failures without a request

If the browser is reporting CORS failures but the services are not showing any requests and responses in the logs, then it most
likely that it is a certificate issue (especially when using self-signed certificates). The target URL can be opened directly in
the browser to determine if the certificate is trusted or not.

##### Provided `Origin` and allowed origins do not match

If the browser is showing an `Origin` header with one value, but that value is not what the server expects (usually configured in
the `PEKKO_HTTP_CORS_ALLOWED_ORIGINS` environment variable of `identity` and `server`) then the request will fail. The proper
allowed origins need to be set and the service(s) restarted.

### Providing secrets and bootstrap parameters

#### Database credentials and `db-*.env` files

These files only include the database usernames and passwords. For clarity, they are split into one set of files for the
`identity` database and one set for the `server` database but that is not required. If a single database (and metrics exporter)
is used, then the configuration can be provided in only one place/file.

> The username/password combination(s) provided here must also be provided to the services in their own files. The relevant
> environment variables are `STASIS_IDENTITY_PERSISTENCE_DATABASE_USER` and `STASIS_IDENTITY_PERSISTENCE_DATABASE_PASSWORD` for
> `identity`, and `STASIS_SERVER_PERSISTENCE_DATABASE_USER` and `STASIS_SERVER_PERSISTENCE_DATABASE_PASSWORD` for `server.`

#### `identity` bootstrap configuration

This configuration prepares the `identity` service for operation and consists of three clients and two users:

* `identity-ui` client - used by the web UI of the `identity` service
* `server-ui` client - used by the web UI of the `server` service
* `server-instance` client - used during data transmission between the server and clients (making outgoing requests)
* `server-node` client - used during data transmission between the server and clients (validating incoming requests)
* `default` user - default management/admin user; should be removed after bootstrap is done (see below)
* `server-management` user - automation user that allows `server` to handle some operations on behalf of users

> Note: The client IDs provided here must be the same as those provided to the services:
> * `STASIS_IDENTITY_BOOTSTRAP_CLIENTS_IDENTITY_UI_CLIENT_ID` should be the same as `IDENTITY_UI_CLIENT_ID`
> * `STASIS_IDENTITY_BOOTSTRAP_CLIENTS_SERVER_UI_CLIENT_ID` should be the same as `SERVER_UI_CLIENT_ID`
> * `STASIS_IDENTITY_BOOTSTRAP_CLIENTS_SERVER_INSTANCE_CLIENT_ID` should be the same as
    `STASIS_SERVER_AUTHENTICATORS_INSTANCE_CLIENT_ID`
> * `STASIS_IDENTITY_BOOTSTRAP_CLIENTS_SERVER_NODE_CLIENT_ID` should be the same as `STASIS_SERVER_AUTHENTICATORS_NODES_AUDIENCE`
> * `STASIS_IDENTITY_BOOTSTRAP_OWNERS_SERVER_MANAGEMENT_USER` should be the same as
    `STASIS_SERVER_CREDENTIALS_MANAGERS_IDENTITY_MANAGEMENT_USER`
> * `STASIS_IDENTITY_BOOTSTRAP_OWNERS_DEFAULT_USER_ID` should be the same as `STASIS_SERVER_SERVICE_BOOTSTRAP_USERS_DEFAULT_ID`
>
> If required, the corresponding client secrets or user passwords also need to be provided.

#### `server` bootstrap configuration

This configuration prepares the `server` service for operation and consists of two schedules, one user and two storage nodes.
Each `*_ID` env var must be set to a random UUID, and it doesn't matter what it is, as long as it is unique. The only exception is
`STASIS_SERVER_SERVICE_BOOTSTRAP_USERS_DEFAULT_ID` which must be set to the same value as
`STASIS_IDENTITY_BOOTSTRAP_OWNERS_DEFAULT_USER_ID`.

#### Secret derivation configuration

The services and clients have the ability to provide extra security for user credentials by hashing them before they are sent out
of any client device. This, however, introduces a bit more complexity to the setup process and the following parameters needs to
be set:

* `STASIS_SERVER_BOOTSTRAP_DEVICES_PARAMETERS_SECRETS_DERIVATION_AUTHENTICATION_SALT_PREFIX`,
  `IDENTITY_UI_DERIVATION_SALT_PREFIX`, `SERVER_UI_DERIVATION_SALT_PREFIX` must be set to the **_same_** **random** value
* `STASIS_SERVER_BOOTSTRAP_DEVICES_PARAMETERS_SECRETS_DERIVATION_ENCRYPTION_SALT_PREFIX` and
  `STASIS_SERVER_BOOTSTRAP_DEVICES_PARAMETERS_SECRETS_DERIVATION_AUTHENTICATION_SALT_PREFIX` must be set to **_different_**
  **random** values

> If this feature is not needed, then the following env vars should be set to `false`: `IDENTITY_UI_PASSWORD_DERIVATION_ENABLED`,
`STASIS_SERVER_BOOTSTRAP_DEVICES_PARAMETERS_SECRETS_DERIVATION_AUTHENTICATION_ENABLED` and `SERVER_UI_PASSWORD_DERIVATION_ENABLED`

#### Redirect URIs

Redirect URIs/URLs are an important part of the OAuth authentication process and need to be configured correctly. The defaults
provided in the compose file should work without any issues but if the DNS names and/or ports of the services are updated, care
must be taken to update the URIs as well.

> :warning: If the DNS names and/or ports are updated **_after_** the bootstrap process is complete, the URIs of the clients
> **CANNOT** be updated (for security reasons). New clients must be created and the appropriate environment variables must be
> updated

## After Bootstrap

1) **Replace management user** - for extra security, a new management user with a new password should be created and
   the existing, default, management user should be removed. This can be done via the server's web UI (`server-ui`) or using the
   `server_create_user.sh` and `server_delete_user.sh` scripts. The new user needs to have the following permissions:
   `view-self,view-privileged, view-public,view-service,manage-self,manage-privileged,manage-service` to be able to
   manage the server.

2) **Create standard user** - for extra security, the management users and the users that own data/backups should be
   separate. As with the management user, the web UI or the `server_create_user.sh` script can be used; the difference between the
   two types is only in the permissions that they are granted - `view-self,view-public,manage-self` for a standard user.
3) **Create devices** - after the standard user has been created, its devices can then be setup; this is done via the web UI or
   the `server_create_device.sh` script.
4) **Bootstrap devices** - for each new device, a bootstrap process needs to be performed and that is done on the
   device itself. Using the web UI or the `server_get_bootstrap_code.sh` script, a new bootstrap code can be generated for a
   specific device.
5) **Run a backup** - after the bootstrap process has been completed for a device, a dataset definition can then be
   created and the first backup can be started. It might be a good idea to revise the backup rules and include/exclude
   additional files or directories.
6) **Setup scheduling** - as a final setup step, backups can be configured to run periodically but that is optional.

## Deployment Components

### [`bootstrap`](./bootstrap)

Contains bootstrap configuration files for `identity` and `server`.

### [`local`](./local)

Default location for the data persisted by the database and `server`; **files in this directory must be generated
locally and should not be part of any commits**.

### [`secrets`](./secrets)

Contains secrets used by `stasis` services; **files in this directory must be generated
locally and should not be part of any commits**.

### [`secrets/templates`](./secrets/templates)

Template environment files for configuring the services via the [
`scripts/server_prepare_deployment.sh`](scripts/server_prepare_deployment.sh) script

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

#### `server_prepare_deployment.sh`

> Downloads and prepares all configuration files needed for a stasis server deployment
