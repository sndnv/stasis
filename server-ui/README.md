# stasis / server-ui

Web-based user interface for the [`server`](../server) service.

## Structure

This submodule is divided into three main packages:

* [`api`](./lib/api) - interaction with `server` API
* [`model`](./lib/model) - data coming from and sent to `server` API
* [`pages`](./lib/pages) - UI components

## Development

To get started with development of `server-ui`, run the [build.py](./deployment/dev/build.py) script; it will pull
all dependencies and build the project.

> To start the required services, use the [docker-compose.yml](./deployment/dev/docker-compose.yml) file.

> To start the dev `server-ui` server, use the [run_server.py](./deployment/dev/run_server.py) script.

> To start the dev browser, use the [run_browser.py](./deployment/dev/run_browser.py) script.

> To run linting and all tests, execute `./qa.py`.
