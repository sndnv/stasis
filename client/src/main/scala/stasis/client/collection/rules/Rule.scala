package stasis.client.collection.rules

import scala.util.matching.Regex
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import stasis.client.collection.rules.exceptions.RuleParsingFailure

final case class Rule(
  operation: Rule.Operation,
  directory: String,
  pattern: String,
  comment: Option[String],
  original: Rule.Original
) {
  def asString: String = {
    val operationAsString = operation match {
      case Rule.Operation.Include => "+"
      case Rule.Operation.Exclude => "-"
    }

    s"$operationAsString $directory $pattern"
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
  private val directoryPattern: Regex = """^(.+?)\s(?=(?:"[^"]*"|[^"])*$)(.+)$""".r

  def extractOperation(operation: String, lineNumber: Int): Try[Operation] =
    operation match {
      case "+"   => Success(Operation.Include)
      case "-"   => Success(Operation.Exclude)
      case other => Failure(new RuleParsingFailure(s"Invalid rule operation provided on line [${lineNumber.toString}]: [$other]"))
    }

  def extractDirectoryPattern(rule: String, lineNumber: Int): Try[(String, String)] =
    rule.trim match {
      case directoryPattern(directory, pattern) =>
        Success((trimQuotedString(directory), trimQuotedString(pattern)))

      case other =>
        Failure(
          new RuleParsingFailure(
            s"Invalid rule directory and/or pattern provided on line [${lineNumber.toString}]: [$other]"
          )
        )
    }

  def trimQuotedString(string: String): String =
    string.trim.replaceAll("^\"|\"$", "").trim

  def apply(line: String, lineNumber: Int): Try[Rule] =
    line match {
      case rule(operation, rule, comment) =>
        for {
          operation <- extractOperation(operation, lineNumber)
          (directory, pattern) <- extractDirectoryPattern(rule, lineNumber)
        } yield {
          Rule(
            operation = operation,
            directory = directory,
            pattern = pattern,
            comment = Option(comment).map(_.trim),
            original = Original(line, lineNumber)
          )
        }

      case other =>
        Failure(new RuleParsingFailure(s"Invalid rule definition found on line [${lineNumber.toString}]: [$other]"))
    }
}
