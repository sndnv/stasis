# stasis / client

Linux / macOS backup client, using [`server`](../server) for management and storage.

This submodule represent the client's background service, responsible for performing all client operations
(backup, recovery, maintenance).

To allow for a frontend (such as [`client-cli`](../client-cli)) to
communicate with the background service, an HTTP API runs when the service is active and (usually) continues
to run in the background regardless of the state of any frontend.

> To prevent unauthorized access, the HTTP API **requires all requests to be made with the appropriate security
> token**. For additional security **the API should always be bound to `localhost`** and **TLS should always be
> configured/used**.
