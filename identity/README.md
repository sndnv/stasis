# stasis / identity

OAuth2 identity management service based on [RFC 6749](https://tools.ietf.org/html/rfc6749).

* The [Management API](./src/main/scala/stasis/identity/api/Manage.scala) is available at `/manage`
* The [OAuth2 API](./src/main/scala/stasis/identity/api/OAuth.scala) is available at `/oauth`
* [JWKs](./src/main/scala/stasis/identity/api/Jwks.scala) can be accessed via `/jwks`

> An example bootstrap config, for first-run service setup, can be found in the
> [resources](./src/main/resources/example-bootstrap.conf).
