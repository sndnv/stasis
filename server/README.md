# stasis / server

Backup management and storage service.

* The [Server API](./src/main/scala/stasis/server/api/ApiEndpoint.scala) is available at `/`
* The [Core Endpoint](../core/src/main/scala/stasis/core/networking/http/HttpEndpoint.scala) is available on a separate
  port (set via [environment or config](./src/main/resources/application.conf))
* Prometheus metrics are available on a separate port (set via [environment or config](./src/main/resources/application.conf))
