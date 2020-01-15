package stasis.client.ops.exceptions

final case class ScheduleRetrievalFailure(val message: String) extends Exception(message)
