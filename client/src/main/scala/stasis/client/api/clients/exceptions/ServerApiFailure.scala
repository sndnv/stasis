package stasis.client.api.clients.exceptions

import akka.http.scaladsl.model.StatusCode

class ServerApiFailure(val status: StatusCode, val message: String) extends Exception(message)
