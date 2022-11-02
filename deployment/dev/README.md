# deployment / dev

The provided `docker-compose.yml` files define all `stasis` services and their configuration, for testing and development
purposes.

The following deployments are available:

* `docker-compose.yml` - default deployment of all services
* `docker-compose-metrics.yml` - deployment of prometheus and grafana only, for collecting client metrics
* `docker-compose-no-auth-has.yml` - default deployment of all services, with **disabled** hashing of user authentication passwords

## Getting Started

1) Generate artifacts and certificates with `./scripts/prepare_deployment.sh <country> <location> <organization>`
2) Generate device secret with `./scripts/generate_device_secret.py --user-id <user-id-on-server> --user-salt <user-salt-on-server> --output-path ../secrets/client.secret`
3) Start services with `docker-compose up` (or `docker compose -f <compose file name> up`)

## Running Tests

`./scripts/run_smoke_test.sh`

## Deployment Components

### [`config`](./config)

Contains configuration files used by `stasis` services.

### [`dockerfiles`](./dockerfiles)

Contains Dockerfiles for services that need some extra customization to make them easier to test.

For example, a single image with `client` and `client-cli` is built to test both components and their integration.

### [`secrets`](./secrets)

Contains secrets used by `stasis` services; **files in this directory must be generated locally and should not be part
of any commits**.

### [`scripts`](./scripts)

Contains scripts that run tests and help with setting up the test environment.

#### `generate_artifacts.py`

> Generate docker images or executables for all runnable components.

```
./generate_artifacts.py           # generates artifacts for all projects/submodules
./generate_artifacts.py -p client # generates artifact for "client" submodule
```

By default, Docker images for `identity`, `identity-ui`, `server`, `client` and `client-cli` will be generated; they are
necessary for running the services in the provided `docker-compose.yml` files.

#### `generate_device_secret.py`

> Generate encrypted device secrets.

```
./generate_device_secret.py --user-id <user-id-on-server> --user-salt <user-salt-on-server>
```

To run the `client` background service, a client/device secret needs to be created. The password of the user needs to be
provided as it is used for encrypting the secret.

#### `generate_self_signed_cert.py`

> Generate new self-signed x509 certificates and private keys.

```
./generate_self_signed_cert.py <country> <location> <organization>
```

The communication between all services is based on HTTPS and certificates are needed to properly set up the relevant
endpoints.

#### `generate_user_password.py`

> Generate hashed user authentication passwords.

```
./generate_user_password.py --user-salt <user-salt-on-server>
```

For testing purposes, a hashed user authentication passwords may need to be generated from the raw user password
(for example, the `run_smoke_test.sh` uses the hashed authentication password for retrieving user tokens).

#### `prepare_deployment.sh`

> Generate artifacts and certificates for deployed services (dev)

```
./prepare_deployment.sh <country> <location> <organization>
```

Combines generating certificates for `identity`, `server` and `client` services via `generate_self_signed_cert.py` and
generating all artifacts via `generate_artifacts.py`.

#### `run_smoke_test.sh`

> Run basic tests against deployed services

```
./run_smoke_test.sh
```

Runs a sequence of commands against the `stasis` services, ranging from simple token retrievals to full backup and
recovery operations, and performs basic sanity checks on the output of those commands.

This script is meant to provide both a limited integration test, and an example of how all services should be configured
and used together.

*Requires all services defined in `docker-compose.yml` to be up and running; all certificates and secrets need to exist
and be available to the services that need them.*
