package stasis.client.collection.rules

import scala.util.matching.Regex
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import stasis.client.collection.rules.exceptions.RuleParsingFailure

final case class Rule(
  operation: Rule.Operation,
  source: String,
  pattern: String,
  options: Map[String, String],
  comment: Option[String],
  original: Rule.Original
) {
  def asString: String = {
    val operationAsString = operation match {
      case Rule.Operation.Include => "+"
      case Rule.Operation.Exclude => "-"
    }

    val optionsAsString = options.toSeq.sortBy(_._1).map { case (key, value) =>
      val valueAsString = if (value.contains(" ")) s""""$value"""" else value
      s"@$key=$valueAsString"
    }

    (Seq(operationAsString, source, pattern) ++ optionsAsString).mkString(" ")
  }
}

object Rule {
  sealed trait Operation
  object Operation {
    case object Include extends Operation
    case object Exclude extends Operation
  }

  final case class Original(
    line: String,
    lineNumber: Int
  )

  private val rule: Regex = """^([+-])(.+?)(?:\s+(?:#|//)(.*))?$""".r
  private val sourcePattern: Regex = """^(.+?)\s(?=(?:"[^"]*"|[^"])*$)(.+)$""".r
  private val optionMarker: Regex = """^(.*?)\s+@([A-Za-z][A-Za-z0-9_.-]*)=("[^"]*"|\S+)\s*$""".r

  def extractOperation(operation: String, lineNumber: Int): Try[Operation] =
    operation match {
      case "+"   => Success(Operation.Include)
      case "-"   => Success(Operation.Exclude)
      case other => Failure(new RuleParsingFailure(s"Invalid rule operation provided on line [${lineNumber.toString}]: [$other]"))
    }

  def extractSourcePattern(rule: String, lineNumber: Int): Try[(String, String)] =
    rule.trim match {
      case sourcePattern(source, pattern) =>
        Success((trimQuotedString(source), trimQuotedString(pattern)))

      case other =>
        Failure(
          new RuleParsingFailure(
            s"Invalid rule source and/or pattern provided on line [${lineNumber.toString}]: [$other]"
          )
        )
    }

  def extractOptions(rule: String, lineNumber: Int): Try[(String, Map[String, String])] = {
    @scala.annotation.tailrec
    def collect(current: String, collected: Map[String, String]): Try[(String, Map[String, String])] =
      current match {
        case optionMarker(rest, key, value) =>
          if (collected.contains(key)) {
            Failure(new RuleParsingFailure(s"Duplicate option [$key] provided on line [${lineNumber.toString}]"))
          } else {
            collect(rest, collected + (key -> trimQuotedString(value)))
          }

        case _ =>
          Success((current, collected))
      }

    collect(rule.trim, Map.empty)
  }

  def trimQuotedString(string: String): String =
    string.trim.replaceAll("^\"|\"$", "").trim

  def apply(line: String, lineNumber: Int): Try[Rule] =
    line match {
      case rule(operation, rule, comment) =>
        for {
          operation <- extractOperation(operation, lineNumber)
          restAndOptions <- extractOptions(rule, lineNumber)
          sourceAndPattern <- extractSourcePattern(restAndOptions._1, lineNumber)
        } yield {
          Rule(
            operation = operation,
            source = sourceAndPattern._1,
            pattern = sourceAndPattern._2,
            options = restAndOptions._2,
            comment = Option(comment).map(_.trim),
            original = Original(line, lineNumber)
          )
        }

      case other =>
        Failure(new RuleParsingFailure(s"Invalid rule definition found on line [${lineNumber.toString}]: [$other]"))
    }
}
