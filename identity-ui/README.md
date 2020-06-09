# stasis / identity-ui

Web-based user interface for the [`identity`](../identity) service.

## Structure

This submodule is divided into three main packages:

* [`api`](./src/api) - interaction with `identity` API
* [`components`](./src/components) - `Vue.js` [components](https://vuejs.org/v2/guide/components.html)
* [`pages`](./src/pages) - `Vue.js` [pages](https://cli.vuejs.org/config/#pages)

## Development

To get started with development of `identity-ui`, run the [install.py](./dev/install.py) script; it starts a Docker
image and installs the necessary modules via `yarn`. Afterwards, using the provided `docker-compose.yml` with
`docker-compose up` is all that is needed to get the development environment up and running.

> To run linting and all tests, execute `./qa.py`.
