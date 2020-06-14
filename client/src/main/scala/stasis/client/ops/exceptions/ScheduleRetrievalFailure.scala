package stasis.client.ops.exceptions

final case class ScheduleRetrievalFailure(message: String) extends Exception(message)
