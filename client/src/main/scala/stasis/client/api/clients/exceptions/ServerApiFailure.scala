package stasis.client.api.clients.exceptions

import org.apache.pekko.http.scaladsl.model.StatusCode

class ServerApiFailure(val status: StatusCode, val message: String) extends Exception(message)
