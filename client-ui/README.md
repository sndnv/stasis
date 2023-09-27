# stasis / client-ui

Desktop user interface for the [`client`](../client) background service.

## Structure

This submodule is divided into three main packages:

* [`api`](./lib/api) - interaction with the `client` API
* [`model`](./lib/model) - data coming from and sent to `client` and `server` APIs
* [`pages`](./lib/pages) - UI components

## Development

To get started with development of `client-ui`, run the [build.py](./deployment/dev/build.py) script; it will pull
all dependencies and build the project.

> To run linting and all tests, execute `./qa.py`.
