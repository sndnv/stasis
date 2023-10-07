# stasis

> A **[stasis](https://en.wikipedia.org/wiki/Stasis_(fiction))** */ˈsteɪsɪs/* or **stasis field**, in science fiction,
> is a confined area of space in which time has been stopped or the contents have been rendered motionless.

`stasis` is a backup and recovery system with an emphasis on security and privacy; no personal information is collected,
no unencrypted data leaves a client device and all encryption keys are fully in the control of their owner.

[![asciicast](https://asciinema.org/a/YMIf9oCMfvrbznnMnCrUMfar9.svg)](https://asciinema.org/a/YMIf9oCMfvrbznnMnCrUMfar9?speed=3)

## Why?

* **Trust Issues** - Do you trust your backup or infrastructure/storage provider with your unencrypted data?
* **Multi-Device** - How many backup providers would you need to cover all types of devices you own?
* **Self-Hosted** - What if your backup provider goes out of business?

## Goals

* Recover user data from total failure or device loss
* Replicate data to local and remote/cloud storage
* Encrypt data before it leaves a device
* Manage all device backups from a single service

*Along with [`provision`](https://github.com/sndnv/provision), the goal is to be able to grab a blank/off-the-shelf
device and recover the original system in an automated and repeatable way.*

## Features

* ***[Client-only Encryption](https://github.com/sndnv/stasis/wiki/Architecture-%3A%3A-Encryption)*** -
  encryption and decryption is done by client applications; the server never deals with unencrypted data or metadata
* ***[Device-only Secrets](https://github.com/sndnv/stasis/wiki/Architecture-%3A%3A-Secrets)*** -
  user credentials and device secrets do not leave the device on which they were entered/generated
* ***[Default Redundancy](https://github.com/sndnv/stasis/wiki/Architecture-%3A%3A-Core-Persistence)*** -
  copies of a device's encrypted data are sent to multiple nodes by default (local and remote)
* ***[Hybrid Data Storage](https://github.com/sndnv/stasis/wiki/Architecture-%3A%3A-Data-Stores)*** -
  various storage backends (**[Apache Geode](https://geode.apache.org/)**, **[Slick](https://scala-slick.org/)**,
  **in-memory**, **file-based**) are supported and used
* ***Secrets Escrow*** -
  (*TODO*) enables storing encrypted device secrets on the server to simplify recovering of a lost or replaced device
* ***Serverless Mode*** -
  (*TODO*) enables creating backups and recovering from them without the presence of a server

## Installation

Official images and binaries are not yet available, but they can be created locally using the existing [dev tools](deployment/dev).

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
* [Docker](https://www.docker.com/get-started)
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

**NOT** production ready but usable

* `identity` / `identity-ui` - *authentication service and web UI* - **complete**
* `server` / `server-ui` - *backup server and web UI* - **operational**; some features are not yet available
* `client` / `client-cli` / `client-ui`- *Linux / macOS client, CLI and UI* - **operational**; some features are not yet available
* `client-android` - *Android client* - **operational**; some features are not yet available;

## Contributing

Contributions are always welcome!

Refer to the [CONTRIBUTING.md](CONTRIBUTING.md) file for more details.

## Versioning

We use [SemVer](http://semver.org/) for versioning.

## License

This project is licensed under the Apache License, Version 2.0 - see the [LICENSE](LICENSE) file for details

> Copyright 2018 https://github.com/sndnv
>
> Licensed under the Apache License, Version 2.0 (the "License");
> you may not use this file except in compliance with the License.
> You may obtain a copy of the License at
>
> http://www.apache.org/licenses/LICENSE-2.0
>
> Unless required by applicable law or agreed to in writing, software
> distributed under the License is distributed on an "AS IS" BASIS,
> WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
> See the License for the specific language governing permissions and
> limitations under the License.
