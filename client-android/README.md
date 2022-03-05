# stasis / client-android

Android backup client, using [`server`](../server) for management and storage.

This submodule is an Android application, responsible for performing all client operations
(backup, recovery, maintenance).

## Development

To get started with development [`AndroidStudio`](https://developer.android.com/studio) is needed.

Additionally, to allow for testing of the client itself using the Android Emulator, the `server` and
`identity` services need to be up and running (see [`deployment/dev`](../deployment/dev) for more info).

Finally, the client itself needs to go through the usual bootstrap process before it can be used/tested.
A script that retrieves bootstrap codes can be found in the [`./dev`](dev) directory.

> To run linting and all tests, execute `./gradlew qa`.

### Service Connections

The client needs a connection to the identity service and to the server itself, however, those are normally
running in docker containers and by default are only available to the host machine; the Android emulator cannot
use `localhost` to access those because that points to its own loopback interface. Instead, the IP address
`10.0.2.2` can be used to access the loopback interface of the host machine.

In order to get access to the services on the host machine you can:
* Have your local network's DNS server resolve the `server` and `identity` names to `10.0.2.2`
* Deploy a DNS server locally (using docker, for example) and have Android [use that server to resolve the names](https://developer.android.com/studio/run/emulator-networking#dns)
* Reconfigure the [`deployment/dev/docker-compose.yml`](../deployment/dev/docker-compose.yml) file to use the
  `10.0.2.2` address for the bootstrap parameters instead of the names

### TLS Certificates

By default, `server` and `identity` have TLS enabled with self-signed certificates. Unlike in the Linux client,
the Android client has no feature that allows accepting self-signed certificates. Instead, the certificates of
the services should be imported so that the OS can validate them properly:

1) Copy the certificates to the device (dragging and dropping them in the emulator is possible; the files end up in `Downloads`)
2) Search for `Encryption` in the settings (different OS versions may put these settings in slightly different locations)
3) Select `Install from SD card` or `Install a certificate` > `CA certificate`
4) Select each certificate file (from `Downloads`, if it drag-and-drop was used)
5) Verify that the installation worked by checking the installed certificates in `Trusted credentials` > `User`; you
   should have two, one for `server` and one for `identity`

> If the `10.0.2.2` IP address is used instead of the DNS names for connecting to the services, the certificates
> need to be generated with a different `subjectAltName`: `subjectAltName=IP:10.0.2.2` instead of `subjectAltName=DNS:<name>`.
