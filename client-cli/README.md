# stasis / client-cli

Command-line interface for the [`client`](../client) background service.

## Structure

This submodule is divided into three main packages:

* [`api`](./client_cli/api) - interaction with `client` API
* [`cli`](./client_cli/cli) - CLI command definitions
* [`render`](./client_cli/render) - command output rendering and API data transformation

## Development

To get started with development of `client-cli`, run `source venv/bin/activate` and `pip install -e .`; this will setup
your [virtual environment](https://docs.python.org/3/tutorial/venv.html) and install the packages needed for `client-cli`.

> To run linting and all tests, execute `./qa.py`.
