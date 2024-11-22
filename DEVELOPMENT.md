## Development

The majority of the code is [Scala](https://scala-lang.org/) so, at the very least, Java (JDK17) and SBT need to be
available on your dev machine.

Some submodules use Python (ex: [`client-cli`](client-cli)), [Flutter](https://flutter.dev/) (ex: [`identity-ui`](identity-ui))
or Kotlin for Android (ex: [`client-android`](client-android)) so the appropriate tools for those platforms need to be
available as well.

[Protobuf](https://developers.google.com/protocol-buffers) is also used, however, it is handled by an
[sbt plugin](https://scalapb.github.io/) and no additional tools are needed.

There are also some Python and Bash [scripts](deployment/dev/scripts) to help with deployment and testing.

###### Downloads / Installation:

* [Adoptium JDK](https://adoptium.net/)
* [Scala](https://scala-lang.org/download/)
* [sbt](https://www.scala-sbt.org/download.html)
* [Python](https://www.python.org/downloads/)
* [Pylint](https://www.pylint.org/#install)
* [Flutter](https://docs.flutter.dev/get-started/install)
* [Docker](https://www.docker.com/get-started) or [Podman](https://podman.io/)
* [AndroidStudio](https://developer.android.com/studio)

### Getting Started

1) Clone or fork the repo
2) Run `sbt qa`

### Submodules

> To execute all tests and QA steps for the Scala submodules, simply run `sbt qa` from the root of the repo.

#### [`assets`](assets)

Image assets used by other submodules.

* Image files and **Python** script(s)
* **Testing** - `n/a`
* **Packaging** - `n/a`

#### [`proto`](proto)

Protocol Buffers file(s) defining gRPC services and messages used by the `core` networking and routing.

* **protobuf** spec
* **Testing** - `n/a`
* **Packaging** - `n/a`

#### [`layers`](layers)

Generic code commonly used by the various layers of the `stasis` services - API, persistence, security, telemetry.

* **Scala** code
* **Testing** - `sbt "project layers" qa`
* **Packaging** - `n/a`

#### [`core`](core)

Core routing, networking and persistence code. Represents the subsystem that handles data exchange.

* **Scala** code
* **Testing** - `sbt "project core" qa`
* **Packaging** - `n/a`

#### [`shared`](shared)

API and model code shared between the `server` and `client` submodules.

* **Scala** code
* **Testing** - `sbt "project shared" qa`
* **Packaging** - `n/a`

#### [`identity`](identity)

OAuth2 identity management service based on [RFC 6749](https://tools.ietf.org/html/rfc6749).

* **Scala** code
* **Testing** - `sbt "project identity" qa`
* **Packaging** - `sbt "project identity" docker:publishLocal`

#### [`identity-ui`](identity-ui)

Web UI for [`identity`](identity).

* **Flutter** code
* **Testing** - `cd ./identity-ui && ./qa.py`
* **Packaging** - `cd ./identity-ui && ./deployment/production/build.py`

#### [`server`](server)

Backup management and storage service.

* **Scala** code
* **Testing** - `sbt "project server" qa`
* **Packaging** - `sbt "project server" docker:publishLocal`

#### [`server-ui`](server-ui)

Web UI for [`server`](server).

* **Flutter** code
* **Testing** - `cd ./server-ui && ./qa.py`
* **Packaging** - `cd ./server-ui && ./deployment/production/build.py`

#### [`client`](client)

Linux / macOS backup client, using `server` for management and storage.

* **Scala** code
* **Testing** - `sbt "project client" qa`
* **Packaging** - `sbt "project client" docker:publishLocal`

#### [`client-cli`](client-cli)

Command-line interface for [`client`](client).

* **Python** code
* **Testing** - `cd ./client-cli && source venv/bin/activate && ./qa.py`
* **Packaging** - `cd ./client-cli && source venv/bin/activate && pip install .`

#### [`client-ui`](client-ui)

Desktop interface for [`client`](client).

* **Flutter** code
* **Testing** - `cd ./client-ui && ./qa.py`

#### [`client-android`](client-android)

Android backup client, using `server` for management and storage.

* **Kotlin** code
* **Testing** - `cd ./client-android && ./gradlew qa`
* **Packaging** - via `AndroidStudio` - `Build` > `Build Bundle(s)/APK(s)` > `Build APK(s)`

#### [`deployment`](deployment)

Deployment, artifact and certificate generation scripts and configuration.

* **Python** and **Bash** code; config files
* **Testing** - `cd ./deployment/dev/scripts && ./run_smoke_test.sh`
* **Packaging** - `see ./deployment/dev/docker-compose.yml`

### Current State

> Ready for prime time but run in production at your own risk!

* `identity` / `identity-ui` - *authentication service and web UI* - **complete**
* `server` / `server-ui` - *backup server and web UI* - **operational**; some features are not yet available
* `client` / `client-cli` / `client-ui`- *Linux / macOS client, CLI and UI* - **operational**; some features are not yet available
* `client-android` - *Android client* - **operational**; some features are not yet available;
